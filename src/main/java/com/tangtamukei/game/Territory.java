package com.tangtamukei.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 청크 땅따먹기.
 * - 안 먹은 땅(주인 없음)은 [일반 땅] 도끼로만 먹을 수 있음 ({@link LandToken})
 * - 다른 팀 땅은 우리 팀만 있는 상태로 CAPTURE_SECONDS 초 머물면 빼앗음 (두 팀 이상 있으면 경합으로 멈춤)
 * - 각 팀의 시작 꼭짓점 2x2 청크는 처음부터 그 팀 땅
 */
public final class Territory {

    public static final int CAPTURE_SECONDS = 5;
    private static final int N = MapLayout.CHUNKS;

    private final GameTeam[][] owner = new GameTeam[N][N];
    private final GameTeam[][] capturing = new GameTeam[N][N];
    private final int[][] progress = new int[N][N];
    /** 땅 주인이 바뀔 때마다 증가 (지도 다시 그리기용). */
    private volatile int version;
    private BukkitTask task;

    public void start(Plugin plugin, World world, GameTeam[] teams, Function<Player, GameTeam> teamOf) {
        stop();
        for (GameTeam[] row : owner) java.util.Arrays.fill(row, null);
        for (int[] row : progress) java.util.Arrays.fill(row, 0);
        // 시작 꼭짓점 2x2 청크
        for (GameTeam t : teams) {
            int c0 = t.spawnX() < 0 ? 0 : N - 2, r0 = t.spawnZ() < 0 ? 0 : N - 2;
            for (int dc = 0; dc < 2; dc++)
                for (int dr = 0; dr < 2; dr++)
                    owner[c0 + dc][r0 + dr] = t;
        }
        version++;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(world, teamOf), 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public GameTeam owner(int col, int row) {
        return owner[col][row];
    }

    public int version() {
        return version;
    }

    /** 블록 좌표의 땅 주인 (맵 밖이면 null). */
    public GameTeam ownerAt(int x, int z) {
        if (!MapLayout.inside(x, z)) return null;
        return owner[(x - MapLayout.MIN) >> 4][(z - MapLayout.MIN) >> 4];
    }

    /** 맵 안이고 아무 팀도 안 먹은 땅이면 true. */
    public boolean isUnclaimed(int x, int z) {
        return MapLayout.inside(x, z) && ownerAt(x, z) == null;
    }

    /** [일반 땅] 도끼로 안 먹은 땅을 먹음. 성공하면 true. */
    public boolean claim(int x, int z, GameTeam team) {
        if (!isUnclaimed(x, z)) return false;
        owner[(x - MapLayout.MIN) >> 4][(z - MapLayout.MIN) >> 4] = team;
        version++;
        return true;
    }

    /** [특수 땅] 도끼로 다른 팀 땅을 빼앗음. 빼앗긴 팀을 반환 (실패하면 null). */
    public GameTeam convert(int x, int z, GameTeam team) {
        GameTeam previous = ownerAt(x, z);
        if (previous == null || previous == team) return null;
        owner[(x - MapLayout.MIN) >> 4][(z - MapLayout.MIN) >> 4] = team;
        version++;
        return previous;
    }

    public int count(GameTeam team) {
        int n = 0;
        for (GameTeam[] row : owner) for (GameTeam t : row) if (t == team) n++;
        return n;
    }

    private void tick(World world, Function<Player, GameTeam> teamOf) {
        Map<Long, Set<GameTeam>> present = new HashMap<>();
        Map<Long, java.util.List<Player>> players = new HashMap<>();
        for (Player p : world.getPlayers()) {
            GameTeam t = teamOf.apply(p);
            if (t == null || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            if (!MapLayout.inside(l.getBlockX(), l.getBlockZ())) continue;
            long k = key((l.getBlockX() - MapLayout.MIN) >> 4, (l.getBlockZ() - MapLayout.MIN) >> 4);
            present.computeIfAbsent(k, x -> EnumSet.noneOf(GameTeam.class)).add(t);
            players.computeIfAbsent(k, x -> new java.util.ArrayList<>()).add(p);
        }

        for (int c = 0; c < N; c++) {
            for (int r = 0; r < N; r++) {
                Set<GameTeam> teams = present.get(key(c, r));
                if (teams == null || teams.size() != 1) {
                    // 아무도 없거나 경합 중: 진행 초기화
                    progress[c][r] = 0;
                    capturing[c][r] = null;
                    if (teams != null) actionBar(players.get(key(c, r)), Component.text("경합 중! 점령이 멈췄어요", NamedTextColor.GOLD));
                    continue;
                }
                GameTeam t = teams.iterator().next();
                if (owner[c][r] == null || owner[c][r] == t) {
                    progress[c][r] = 0;
                    continue;
                }
                if (capturing[c][r] != t) {
                    capturing[c][r] = t;
                    progress[c][r] = 0;
                }
                progress[c][r]++;
                java.util.List<Player> here = players.get(key(c, r));
                if (progress[c][r] >= CAPTURE_SECONDS) {
                    GameTeam previous = owner[c][r];
                    owner[c][r] = t;
                    progress[c][r] = 0;
                    version++;
                    actionBar(here, Component.text("청크 점령! (" + count(t) + "칸)", t.color));
                    for (Player p : here) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
                    if (previous != null) notifyLost(world, teamOf, previous, c, r);
                } else {
                    actionBar(here, bar(progress[c][r], t));
                }
            }
        }
    }

    private static void notifyLost(World world, Function<Player, GameTeam> teamOf, GameTeam team, int c, int r) {
        int x = MapLayout.MIN + c * 16 + 8, z = MapLayout.MIN + r * 16 + 8;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamOf.apply(p) == team) {
                p.sendMessage(Component.text("땅을 빼앗겼어요! (" + x + ", " + z + ")", NamedTextColor.RED));
            }
        }
    }

    private static Component bar(int progress, GameTeam team) {
        StringBuilder filled = new StringBuilder(), empty = new StringBuilder();
        for (int i = 0; i < CAPTURE_SECONDS; i++) (i < progress ? filled : empty).append('■');
        return Component.text("점령 중 ", NamedTextColor.WHITE)
                .append(Component.text(filled.toString(), team.color))
                .append(Component.text(empty.toString(), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + progress + "/" + CAPTURE_SECONDS, NamedTextColor.GRAY));
    }

    private static void actionBar(java.util.List<Player> players, Component c) {
        if (players != null) for (Player p : players) p.sendActionBar(c);
    }

    private static long key(int c, int r) {
        return ((long) c << 32) | (r & 0xFFFFFFFFL);
    }
}
