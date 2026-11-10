package com.tangtamukei.game;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BiomeSearchResult;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 오버월드 "오려 붙이기": 숨겨진 야생 월드(소스)에서 칸마다 조건에 맞는 곳을 찾아
 * 그 영역의 블록·바이옴·상자 등·몹을 게임 맵의 해당 칸에 그대로 복사.
 * 칸끼리는 다른 곳에서 가져온 지형이라 청크 경계가 뚝뚝 끊겨 보임.
 */
public final class PastedOverworld {

    /** 복사 영역: 게임 맵 (targetX, targetZ)부터 size 블록 = 소스 (srcX, srcZ)부터. */
    public record Region(int id, int targetX, int targetZ, int size, int srcX, int srcZ, String what) {
    }

    private static final int SEARCH_SPREAD = 8000;   // 소스 월드에서 찾기 시작할 위치 범위
    private static final int BIOME_RADIUS = 3000;
    private static final int STRUCTURE_RADIUS = 120; // 청크

    private final Plugin plugin;
    private final World source;
    private final Random random;
    private final Region[][] byChunk = new Region[MapLayout.CHUNKS][MapLayout.CHUNKS];
    private final List<Region> regions = new ArrayList<>();
    private final Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
    private final Set<Biome> biomes = new LinkedHashSet<>();
    private Location portal; // 게임 맵 기준 엔드 포탈 중심

    private PastedOverworld(Plugin plugin, World source, long seed) {
        this.plugin = plugin;
        this.source = source;
        this.random = new Random(seed);
    }

    // ───────────── 준비 (소스 월드에서 찾기 → 불러오기 → 스냅샷) ─────────────

    /**
     * 칸마다 소스 위치를 찾고 필요한 청크를 불러와 스냅샷까지 뜸. 메인 스레드에서 한 틱에 하나씩 진행.
     * progress 로 진행 상황 문자열을 알림.
     */
    public static CompletableFuture<PastedOverworld> prepare(Plugin plugin, World source, MapLayout layout,
                                                             long seed, Consumer<String> progress) {
        PastedOverworld p = new PastedOverworld(plugin, source, seed);
        CompletableFuture<PastedOverworld> done = new CompletableFuture<>();
        Deque<MapLayout.Request> queue = new ArrayDeque<>(layout.requests());
        int total = queue.size();
        long t0 = System.currentTimeMillis();

        // 1) 한 틱에 하나씩 위치 찾기 (찾기는 메인 스레드에서만 가능)
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            MapLayout.Request req = queue.poll();
            if (req == null) {
                task.cancel();
                long t1 = System.currentTimeMillis();
                p.loadAndSnapshot(progress).thenRun(() -> {
                    plugin.getLogger().info("지형 찾기 " + (t1 - t0) / 1000.0 + "초, 청크 " + p.snapshots.size()
                            + "개 불러오기 " + (System.currentTimeMillis() - t1) / 1000.0 + "초");
                    done.complete(p);
                })
                        .exceptionally(t -> { done.completeExceptionally(t); return null; });
                return;
            }
            progress.accept("지형 찾는 중 " + (total - queue.size()) + "/" + total);
            p.resolve(req);
        }, 1L, 1L);
        return done;
    }

    private final List<CompletableFuture<Void>> pending = new ArrayList<>();

    /** 요청 하나의 소스 위치 결정 (구조물이면 경계 상자 중심, 요새면 엔드 포탈 중심). */
    private CompletableFuture<Region> resolve(MapLayout.Request req) {
        CompletableFuture<Region> result = new CompletableFuture<>();
        pending.add(result.thenAccept(r -> { }));
        Structure structure = req.variant().structure();

        if (structure == null) {
            Location found = findBiome(req.variant().biome());
            result.complete(addRegion(req, aligned(found.getBlockX(), req.size()), aligned(found.getBlockZ(), req.size()),
                    req.variant().biome().getKey().value()));
            return result;
        }

        Location start = findStructure(structure);
        if (start == null) {
            // 못 찾으면 평원으로 대신
            Location found = findBiome(Biome.PLAINS);
            result.complete(addRegion(req, aligned(found.getBlockX(), req.size()), aligned(found.getBlockZ(), req.size()), "plains"));
            return result;
        }
        source.getChunkAtAsync(start.getBlockX() >> 4, start.getBlockZ() >> 4, true).thenAccept(chunk -> {
            BoundingBox box = structureBox(chunk, structure);
            if (structure == Structure.STRONGHOLD && box != null) {
                findPortal(box).thenAccept(center -> {
                    int cx = center != null ? center[0] : (int) box.getCenterX();
                    int cz = center != null ? center[2] : (int) box.getCenterZ();
                    // 포탈 3x3 가운데 블록이 게임 맵 (0, 0)에 오도록
                    int sx = cx - (0 - req.targetX()), sz = cz - (0 - req.targetZ());
                    if (center != null) portal = new Location(null, 0.5, center[1], 0.5);
                    result.complete(addRegion(req, sx, sz, "stronghold"));
                });
                return;
            }
            double ccx = box != null ? box.getCenterX() : start.getX();
            double ccz = box != null ? box.getCenterZ() : start.getZ();
            result.complete(addRegion(req, aligned((int) Math.floor(ccx), req.size()), aligned((int) Math.floor(ccz), req.size()),
                    structure.getKey().value()));
        });
        return result;
    }

    /** center 를 가운데에 두는 size 영역의 시작점을 청크 격자에 맞춤 (불러올 청크 수 절약). */
    private static int aligned(int center, int size) {
        return Math.round((center - size / 2f) / 16f) * 16;
    }

    private Location randomOrigin() {
        return new Location(source, random.nextInt(-SEARCH_SPREAD, SEARCH_SPREAD), 64, random.nextInt(-SEARCH_SPREAD, SEARCH_SPREAD));
    }

    private final Set<Long> usedChunks = new HashSet<>();

    /** 바이옴 찾기. 다른 칸에서 이미 가져온 청크는 건너뜀. */
    private Location findBiome(Biome biome) {
        for (int attempt = 0; attempt < 8; attempt++) {
            BiomeSearchResult r = source.locateNearestBiome(randomOrigin(), BIOME_RADIUS, biome);
            if (r != null && usedChunks.add(key(r.getLocation().getBlockX() >> 4, r.getLocation().getBlockZ() >> 4))) {
                return r.getLocation();
            }
        }
        BiomeSearchResult r = source.locateNearestBiome(randomOrigin(), BIOME_RADIUS * 2, Biome.PLAINS, Biome.FOREST);
        return r != null ? r.getLocation() : randomOrigin();
    }

    private final Set<Long> usedStarts = new HashSet<>();

    /** 다른 칸에서 이미 쓴 구조물은 건너뜀. */
    private Location findStructure(Structure structure) {
        for (int attempt = 0; attempt < 6; attempt++) {
            StructureSearchResult r = source.locateNearestStructure(randomOrigin(), structure, STRUCTURE_RADIUS, false);
            if (r != null && usedStarts.add(key(r.getLocation().getBlockX() >> 4, r.getLocation().getBlockZ() >> 4))) {
                return r.getLocation();
            }
        }
        return null;
    }

    private static BoundingBox structureBox(Chunk chunk, Structure structure) {
        Collection<GeneratedStructure> list = chunk.getStructures(structure);
        return list.isEmpty() ? null : list.iterator().next().getBoundingBox();
    }

    /** 요새 경계 상자 안의 청크를 불러와 엔드 포탈 틀 12개의 가운데를 찾음. {x, y, z} */
    private CompletableFuture<int[]> findPortal(BoundingBox box) {
        int minCx = (int) Math.floor(box.getMinX()) >> 4, maxCx = (int) Math.floor(box.getMaxX()) >> 4;
        int minCz = (int) Math.floor(box.getMinZ()) >> 4, maxCz = (int) Math.floor(box.getMaxZ()) >> 4;
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++)
                loads.add(source.getChunkAtAsync(cx, cz, true));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApply(v -> {
            long sx = 0, sz = 0;
            int n = 0, y = 0;
            int minY = Math.max(source.getMinHeight(), (int) box.getMinY()), maxY = Math.min(source.getMaxHeight() - 1, (int) box.getMaxY());
            for (CompletableFuture<Chunk> f : loads) {
                Chunk chunk = f.join();
                ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        for (int yy = minY; yy <= maxY; yy++)
                            if (snap.getBlockType(x, yy, z) == Material.END_PORTAL_FRAME) {
                                sx += (chunk.getX() << 4) + x;
                                sz += (chunk.getZ() << 4) + z;
                                y = yy;
                                n++;
                            }
            }
            if (n == 0) return null;
            return new int[]{(int) Math.round((double) sx / n), y, (int) Math.round((double) sz / n)};
        });
    }

    private synchronized Region addRegion(MapLayout.Request req, int sx, int sz, String what) {
        Region r = new Region(regions.size() + 1, req.targetX(), req.targetZ(), req.size(), sx, sz, what);
        regions.add(r);
        for (int dx = 0; dx < req.size(); dx += 16)
            for (int dz = 0; dz < req.size(); dz += 16)
                byChunk[(req.targetX() + dx - MapLayout.MIN) >> 4][(req.targetZ() + dz - MapLayout.MIN) >> 4] = r;
        return r;
    }

    /** 모든 영역의 소스 청크를 불러와 고정하고 스냅샷(블록 + 바이옴) 저장. */
    private CompletableFuture<Void> loadAndSnapshot(Consumer<String> progress) {
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).thenCompose(v -> {
            Set<Long> needed = new HashSet<>();
            for (Region r : regions) {
                for (int cx = r.srcX() >> 4; cx <= (r.srcX() + r.size() - 1) >> 4; cx++)
                    for (int cz = r.srcZ() >> 4; cz <= (r.srcZ() + r.size() - 1) >> 4; cz++)
                        needed.add(key(cx, cz));
            }
            int total = needed.size();
            int[] count = {0};
            List<CompletableFuture<?>> loads = new ArrayList<>();
            for (long k : needed) {
                loads.add(source.getChunkAtAsync((int) (k >> 32), (int) k, true).thenAccept(chunk -> {
                    chunk.addPluginChunkTicket(plugin);
                    snapshots.put(key(chunk.getX(), chunk.getZ()), chunk.getChunkSnapshot(true, true, false));
                    if (++count[0] % 16 == 0 || count[0] == total) progress.accept("지형 불러오는 중 " + count[0] + "/" + total);
                }));
            }
            return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
        }).thenRun(this::collectBiomes);
    }

    private void collectBiomes() {
        for (Region r : regions) {
            for (int x = 0; x < r.size(); x += 4)
                for (int z = 0; z < r.size(); z += 4)
                    for (int y = source.getMinHeight(); y < source.getMaxHeight(); y += 8)
                        biomes.add(sourceBiome(r.srcX() + x, y, r.srcZ() + z));
        }
        biomes.add(Biome.PLAINS);
    }

    // ───────────── 생성기에서 쓰는 조회 (스냅샷만 읽으므로 스레드 안전) ─────────────

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** 게임 맵 블록 좌표 → 영역 (맵 밖이면 가장 가까운 영역). */
    public Region regionAt(int x, int z) {
        int c = Math.clamp(Math.floorDiv(x - MapLayout.MIN, 16), 0, MapLayout.CHUNKS - 1);
        int r = Math.clamp(Math.floorDiv(z - MapLayout.MIN, 16), 0, MapLayout.CHUNKS - 1);
        return byChunk[c][r];
    }

    public ChunkSnapshot snapshot(int sx, int sz) {
        return snapshots.get(key(sx >> 4, sz >> 4));
    }

    public Biome sourceBiome(int sx, int y, int sz) {
        ChunkSnapshot snap = snapshot(sx, sz);
        return snap == null ? Biome.PLAINS : snap.getBiome(sx & 15, y, sz & 15);
    }

    public Set<Biome> biomes() {
        return biomes;
    }

    /** 청크별 영역 번호 (같은 번호 = 한 곳에서 이어서 가져온 지형). [chunkCol][chunkRow] */
    public int[][] groups() {
        int[][] g = new int[MapLayout.CHUNKS][MapLayout.CHUNKS];
        for (int c = 0; c < MapLayout.CHUNKS; c++)
            for (int r = 0; r < MapLayout.CHUNKS; r++)
                g[c][r] = byChunk[c][r] == null ? 0 : byChunk[c][r].id();
        return g;
    }

    public List<Region> regions() {
        return regions;
    }

    /** 엔드 포탈 중심 (게임 맵 좌표, 월드는 비어 있음). 못 찾았으면 null. */
    public Location portal() {
        return portal;
    }

    // ───────────── 생성 후: 상자·스포너 등 블록 상태와 몹 복사 ─────────────

    /** 게임 월드가 만들어진 뒤 메인 스레드에서 호출. 복사한 엔티티 수를 반환. */
    public int copyStatesAndEntities(World target) {
        int entities = 0;
        for (Region r : regions) {
            for (int cx = r.srcX() >> 4; cx <= (r.srcX() + r.size() - 1) >> 4; cx++) {
                for (int cz = r.srcZ() >> 4; cz <= (r.srcZ() + r.size() - 1) >> 4; cz++) {
                    Chunk chunk = source.getChunkAt(cx, cz);
                    for (BlockState state : chunk.getTileEntities()) {
                        Location to = toTarget(r, state.getX(), state.getY(), state.getZ(), target);
                        if (to == null) continue;
                        state.copy(to).update(true, false);
                    }
                    for (Entity e : chunk.getEntities()) {
                        if (e instanceof Player) continue;
                        Location loc = e.getLocation();
                        Location to = toTarget(r, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), target);
                        if (to == null) continue;
                        to.setX(loc.getX() - r.srcX() + r.targetX());
                        to.setZ(loc.getZ() - r.srcZ() + r.targetZ());
                        to.setY(loc.getY());
                        to.setYaw(loc.getYaw());
                        to.setPitch(loc.getPitch());
                        if (e.copy(to) != null) entities++;
                    }
                }
            }
        }
        return entities;
    }

    /** 소스 좌표가 영역 안이면 게임 맵 좌표로. */
    private static Location toTarget(Region r, int sx, int sy, int sz, World target) {
        int dx = sx - r.srcX(), dz = sz - r.srcZ();
        if (dx < 0 || dz < 0 || dx >= r.size() || dz >= r.size()) return null;
        return new Location(target, r.targetX() + dx, sy, r.targetZ() + dz);
    }

    public void release() {
        source.removePluginChunkTickets(plugin);
    }
}
