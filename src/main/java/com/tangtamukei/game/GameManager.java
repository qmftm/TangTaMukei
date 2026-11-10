package com.tangtamukei.game;

import com.tangtamukei.lobby.LobbyManager;
import com.tangtamukei.lobby.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 게임 진행: 맵 생성 → 팀 나누기 → 꼭짓점 스폰 → 종료 시 맵 삭제.
 */
public final class GameManager {

    public enum State { IDLE, GENERATING, RUNNING }

    private static final String WORLD_PREFIX = "tt_game_";
    /** 큰 구조물이 맵 밖으로 삐져나올 수 있어서 바깥 여유 청크도 로드 (나중에 지움). */
    private static final int MARGIN_CHUNKS = 8;
    /** 엔드 월드보더: 메인 섬(반지름 약 140)만 들어오는 크기. 바깥 섬은 1000블록 밖이라 안 보임. */
    public static final int END_BORDER = 300;
    /** 엔드 도착 지점 (바닐라처럼 흑요석 발판 생성). */
    private static final int END_ARRIVAL_X = 100, END_ARRIVAL_Y = 49;

    private final JavaPlugin plugin;
    private final LobbyManager lobby;
    private final MapLayout layout = new MapLayout();
    private final NetherLayout netherLayout = new NetherLayout();
    private final Map<UUID, GameTeam> teams = new HashMap<>();

    private State state = State.IDLE;
    private World overworld;
    private World nether;
    private World end;
    private Location strongholdTarget;

    public GameManager(JavaPlugin plugin, LobbyManager lobby) {
        this.plugin = plugin;
        this.lobby = lobby;
        deleteLeftoverWorlds();
        LogFilter.install();
    }

    public State state() {
        return state;
    }

    public boolean isGameWorld(World world) {
        return world != null && (world.equals(overworld) || world.equals(nether) || world.equals(end));
    }

    public World end() {
        return end;
    }

    public World overworld() {
        return overworld;
    }

    public World nether() {
        return nether;
    }

    public MapLayout layout() {
        return layout;
    }

    /** 엔더의 눈이 날아갈 곳 (요새 엔드 포탈 방). */
    public Location strongholdTarget() {
        return strongholdTarget;
    }

    public GameTeam teamOf(Player player) {
        return state == State.RUNNING ? teams.get(player.getUniqueId()) : null;
    }

    // ───────────── 시작 ─────────────

    public boolean start(int teamCount) {
        if (state != State.IDLE) return false;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        state = State.GENERATING;
        int count = Math.clamp(teamCount <= 0 ? Math.max(players.size(), 1) : teamCount, 1, 4);
        broadcast("<yellow>맵 생성 중... 잠시 기다려 주세요. <gray>(" + count + "팀)");

        long seed = ThreadLocalRandom.current().nextLong();
        String name = WORLD_PREFIX + System.currentTimeMillis();

        overworld = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .seed(seed)
                .generator(new OverworldGenerator(layout, seed))
                .generateStructures(false)
                .createWorld();
        if (overworld == null) {
            fail();
            return true;
        }
        setBorder(overworld, MapLayout.SIZE);
        overworld.setSpawnLocation(0, overworld.getHighestBlockYAt(0, 0) + 1, 0);

        findFortressSeed(1).thenAccept(netherSeed -> {
            if (state != State.GENERATING) return;
            nether = new WorldCreator(name + "_nether")
                    .environment(World.Environment.NETHER)
                    .seed(netherSeed)
                    .generator(new NetherGenerator(netherLayout))
                    .generateStructures(false)
                    .createWorld();
            end = new WorldCreator(name + "_end")
                    .environment(World.Environment.THE_END)
                    .seed(seed)
                    .createWorld();
            if (nether == null || end == null) {
                fail();
                return;
            }
            setBorder(nether, MapLayout.SIZE);
            setBorder(end, END_BORDER);

            loadArea(overworld, "오버월드")
                    .thenCompose(v -> loadArea(nether, "네더"))
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> placeStructures(players, count)));
        });
        return true;
    }

    private void fail() {
        broadcast("<red>맵 생성 실패");
        state = State.IDLE;
        cleanupWorlds();
    }

    private static void setBorder(World w, int size) {
        WorldBorder border = w.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(size);
        border.setWarningDistance(8);
    }

    /**
     * 네더 요새가 맵 밖으로 삐져나오지 않는 시드 찾기.
     * 요새 모양은 (시드, 청크 위치)만으로 정해지므로, 빈 테스트 월드에 같은 시드로 먼저 놓아보고
     * 맵 밖에 블록이 생기면 시드를 바꿔 다시 시도.
     */
    private CompletableFuture<Long> findFortressSeed(int attempt) {
        long seed = ThreadLocalRandom.current().nextLong();
        actionBar("<yellow>네더 요새 위치 확인 중 <white>(" + attempt + ")");
        World test = new WorldCreator(WORLD_PREFIX + "test_" + System.nanoTime())
                .environment(World.Environment.NETHER)
                .seed(seed)
                .generator(new com.tangtamukei.lobby.VoidGenerator(64))
                .generateStructures(false)
                .createWorld();
        if (test == null) return CompletableFuture.completedFuture(seed);

        int r = (MapLayout.MAX >> 4) + 2; // 맵보다 2청크 넓게
        return loadChunks(test, -r, r - 1, null).thenCompose(v -> {
            place(test, NetherLayout.FORTRESS, 64);
            boolean inside = structureFitsInMap(test, -r, r - 1);
            File folder = test.getWorldFolder();
            Bukkit.unloadWorld(test, false);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteFolder(folder));
            if (inside || attempt >= 15) {
                if (!inside) plugin.getLogger().warning("맵 안에 들어오는 네더 요새 시드를 찾지 못했습니다.");
                return CompletableFuture.completedFuture(seed);
            }
            return findFortressSeed(attempt + 1);
        });
    }

    /** 빈 월드에서: 맵 안에 블록이 있고(배치 성공) 맵 밖은 비어 있으면 true. */
    private static boolean structureFitsInMap(World world, int min, int max) {
        boolean placed = false;
        int sections = (world.getMaxHeight() - world.getMinHeight()) >> 4;
        for (int cx = min; cx <= max; cx++) {
            for (int cz = min; cz <= max; cz++) {
                ChunkSnapshot snap = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                boolean empty = true;
                for (int s = 0; s < sections && empty; s++) empty = snap.isSectionEmpty(s);
                if (empty) continue;
                if (!MapLayout.insideChunk(cx, cz)) return false;
                placed = true;
            }
        }
        return placed;
    }

    /** 맵 + 여유 청크를 비동기로 생성/로드하고 고정. */
    private CompletableFuture<Void> loadArea(World world, String label) {
        return loadChunks(world, MapLayout.MIN_CHUNK - MARGIN_CHUNKS, MapLayout.MAX_CHUNK + MARGIN_CHUNKS, label);
    }

    private CompletableFuture<Void> loadChunks(World world, int min, int max, String label) {
        int total = (max - min + 1) * (max - min + 1);
        AtomicInteger done = new AtomicInteger();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int cx = min; cx <= max; cx++) {
            for (int cz = min; cz <= max; cz++) {
                futures.add(world.getChunkAtAsync(cx, cz, true).thenAccept(chunk -> {
                    chunk.addPluginChunkTicket(plugin);
                    int n = done.incrementAndGet();
                    if (label != null && (n % 64 == 0 || n == total)) {
                        actionBar("<yellow>" + label + " 생성 중 <white>" + (n * 100 / total) + "%");
                    }
                }));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /** 바닐라 /place structure 로 구조물을 한 틱에 하나씩 놓음. */
    private void placeStructures(List<Player> players, int count) {
        if (state != State.GENERATING) return;
        Deque<Runnable> jobs = new ArrayDeque<>();
        for (MapLayout.Placement p : layout.placements()) jobs.add(() -> place(overworld, p, 64));
        for (MapLayout.Placement p : netherLayout.placements()) jobs.add(() -> place(nether, p, 64));
        int total = jobs.size();

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (state != State.GENERATING) {
                task.cancel();
                return;
            }
            Runnable job = jobs.poll();
            if (job != null) {
                job.run();
                actionBar("<yellow>구조물 배치 중 <white>" + (total - jobs.size()) + "/" + total);
                return;
            }
            task.cancel();
            clearOutside(overworld);
            clearOutside(nether);
            strongholdTarget = findPortalRoom();
            releaseChunks();
            begin(players, count);
        }, 1L, 1L);
    }

    private void place(World world, MapLayout.Placement p, int y) {
        String cmd = "execute in " + world.getKey().asString() + " run place structure "
                + p.structure().getKey().asString() + " " + p.x() + " " + y + " " + p.z();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    /** 맵 밖으로 삐져나온 구조물 블록/엔티티 제거 (맵 밖은 공허 유지). */
    private void clearOutside(World world) {
        int min = MapLayout.MIN_CHUNK - MARGIN_CHUNKS, max = MapLayout.MAX_CHUNK + MARGIN_CHUNKS;
        int minY = world.getMinHeight();
        for (int cx = min; cx <= max; cx++) {
            for (int cz = min; cz <= max; cz++) {
                if (MapLayout.insideChunk(cx, cz)) continue;
                Chunk chunk = world.getChunkAt(cx, cz);
                for (Entity e : chunk.getEntities()) {
                    if (!(e instanceof Player)) e.remove();
                }
                ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
                int sections = (world.getMaxHeight() - minY) >> 4;
                for (int s = 0; s < sections; s++) {
                    if (snap.isSectionEmpty(s)) continue;
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++)
                            for (int y = minY + s * 16; y < minY + s * 16 + 16; y++)
                                if (!snap.getBlockType(x, y, z).isAir())
                                    chunk.getBlock(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /** 요새 엔드 포탈 방 찾기: 좀벌레 스포너 위치. 못 찾으면 맵 중앙 지하. */
    private Location findPortalRoom() {
        for (int cx = MapLayout.MIN_CHUNK; cx <= MapLayout.MAX_CHUNK; cx++) {
            for (int cz = MapLayout.MIN_CHUNK; cz <= MapLayout.MAX_CHUNK; cz++) {
                for (var state : overworld.getChunkAt(cx, cz).getTileEntities()) {
                    if (state instanceof org.bukkit.block.CreatureSpawner spawner
                            && spawner.getSpawnedType() == org.bukkit.entity.EntityType.SILVERFISH) {
                        return state.getLocation().add(0.5, 0, 0.5);
                    }
                }
            }
        }
        plugin.getLogger().warning("요새 엔드 포탈 방을 찾지 못했습니다.");
        return new Location(overworld, 0.5, 30, 0.5);
    }

    private void releaseChunks() {
        for (World w : List.of(overworld, nether)) w.removePluginChunkTickets(plugin);
    }

    /** 팀 나누고 꼭짓점으로 이동. */
    private void begin(List<Player> players, int count) {
        players.removeIf(p -> !p.isOnline());
        Collections.shuffle(players);
        GameTeam[] used = GameTeam.forCount(count);

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (GameTeam t : used) {
            Team team = board.getTeam(teamId(t));
            if (team == null) team = board.registerNewTeam(teamId(t));
            team.color(t.color);
            team.prefix(Component.text("[" + t.displayName + "] ", t.color));
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }

        teams.clear();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            GameTeam t = used[i % used.length];
            teams.put(player.getUniqueId(), t);
            board.getTeam(teamId(t)).addEntity(player);

            Location spawn = teamSpawn(t);
            player.setRespawnLocation(spawn, true);
            player.teleportAsync(spawn).thenRun(() -> preparePlayer(player, t));
        }
        state = State.RUNNING;
        broadcast("<green><bold>게임 시작!</bold> <gray>" + used.length + "팀, " + players.size() + "명");
    }

    private void preparePlayer(Player player, GameTeam team) {
        lobby.resetVisibility(player);
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setLevel(0);
        player.setExp(0f);
        player.clearActivePotionEffects();
        player.showTitle(Title.title(
                Component.text(team.displayName + " 팀", team.color),
                Text.parse("<white>게임 시작!")));
    }

    /** 엔드 도착 지점 + 흑요석 발판 (바닐라와 같은 5x5). */
    public Location endArrival() {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                end.getBlockAt(END_ARRIVAL_X + dx, END_ARRIVAL_Y - 1, dz).setType(Material.OBSIDIAN);
                for (int dy = 0; dy < 3; dy++) end.getBlockAt(END_ARRIVAL_X + dx, END_ARRIVAL_Y + dy, dz).setType(Material.AIR);
            }
        }
        return new Location(end, END_ARRIVAL_X + 0.5, END_ARRIVAL_Y, 0.5, 90f, 0f);
    }

    /** 리스폰 위치: 침대 등이 게임 월드에 있으면 그곳, 아니면 팀 스폰. */
    public Location respawnFor(Player player) {
        Location bed = player.getRespawnLocation();
        if (bed != null && isGameWorld(bed.getWorld()) && !bed.getWorld().equals(end)) return bed;
        GameTeam team = teamOf(player);
        return team != null ? teamSpawn(team) : overworld.getSpawnLocation();
    }

    public Location teamSpawn(GameTeam team) {
        int x = team.spawnX(), z = team.spawnZ();
        return new Location(overworld, x + 0.5, overworld.getHighestBlockYAt(x, z) + 1, z + 0.5);
    }

    private static String teamId(GameTeam t) {
        return "tt_" + t.name().toLowerCase();
    }

    // ───────────── 종료 ─────────────

    public boolean stop() {
        if (state == State.IDLE) return false;
        state = State.IDLE;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (GameTeam t : GameTeam.values()) {
            Team team = board.getTeam(teamId(t));
            if (team != null) team.unregister();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (teams.containsKey(player.getUniqueId()) || isGameWorld(player.getWorld())) {
                player.setRespawnLocation(null);
                lobby.teleportToLobby(player);
            }
        }
        teams.clear();
        broadcast("<yellow>게임이 종료되었습니다.");

        // 텔레포트가 끝난 뒤 월드 삭제
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanupWorlds, 40L);
        return true;
    }

    private void cleanupWorlds() {
        List<File> folders = new ArrayList<>();
        for (World w : new World[]{overworld, nether, end}) {
            if (w == null) continue;
            for (Player p : w.getPlayers()) p.teleport(lobby.spawn());
            folders.add(w.getWorldFolder());
            Bukkit.unloadWorld(w, false);
        }
        overworld = null;
        nether = null;
        end = null;
        strongholdTarget = null;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> folders.forEach(GameManager::deleteFolder));
    }

    /** 서버가 게임 도중 꺼졌을 때 남은 맵 폴더 정리. */
    private void deleteLeftoverWorlds() {
        File main = Bukkit.getWorlds().getFirst().getWorldFolder();
        for (File dir : new File[]{Bukkit.getWorldContainer(), new File(main, "dimensions/minecraft")}) {
            File[] children = dir.listFiles((d, n) -> n.startsWith(WORLD_PREFIX));
            if (children == null) continue;
            for (File f : children) {
                if (Bukkit.getWorld(f.getName()) == null) deleteFolder(f);
            }
        }
    }

    private static void deleteFolder(File folder) {
        if (folder == null || !folder.exists()) return;
        try (Stream<Path> walk = Files.walk(folder.toPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    public void shutdown() {
        if (state != State.IDLE) {
            state = State.IDLE;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isGameWorld(p.getWorld())) p.teleport(lobby.spawn());
            }
            cleanupWorldsSync();
        }
    }

    private void cleanupWorldsSync() {
        for (World w : new World[]{overworld, nether, end}) {
            if (w == null) continue;
            File folder = w.getWorldFolder();
            Bukkit.unloadWorld(w, false);
            deleteFolder(folder);
        }
        overworld = null;
        nether = null;
        end = null;
    }

    // ───────────── 유틸 ─────────────

    private void broadcast(String mini) {
        Bukkit.broadcast(Text.parse("<gold>[게임]</gold> " + mini));
    }

    private void actionBar(String mini) {
        Component c = Text.parse(mini);
        for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar(c);
    }
}
