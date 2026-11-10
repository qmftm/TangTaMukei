package com.tangtamukei.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.EntityType;

import org.bukkit.ChunkSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 요새의 엔드 포탈 방을 맵 정중앙으로 옮김.
 * 요새 모양은 시드마다 랜덤이라 원래 포탈 방(틀 12개 + 좀벌레 스포너)을 없애고,
 * 포탈 3x3 중심이 (0, 0)에 오는 포탈 방을 지하에 새로 지음.
 *
 * <pre>
 *   위에서 본 모습 (x →, z ↓)       옆에서 본 모습
 *   ███████████                     ███████████  y+7 천장
 *   █    S    █   S = 좀벌레 스포너  █         █
 *   █  ┌─────┐█                     █   ▄▄▄   █  y+3 포탈 틀
 *   █  │ ▓▓▓ │█   ▓ = 포탈(아래 용암) █  ▀▀▀▀▀  █  y+1~2 단상
 *   █  └─────┘█                     ███████████  y   바닥
 *   █    ≡    █   ≡ = 계단
 *   ███████████
 * </pre>
 */
final class PortalRoom {

    static final int FLOOR_Y = 20;
    private static final int HALF_X = 5, HALF_Z = 7, HEIGHT = 7;

    private PortalRoom() {
    }

    /** 원래 포탈 방 제거 → 중앙에 새 포탈 방. 엔더의 눈이 향할 위치 반환. */
    static Location relocate(World world, Random random) {
        removeOriginals(world);
        build(world, random);
        connect(world, random);
        return new Location(world, 0.5, FLOOR_Y + 3, 0.5);
    }

    private static void removeOriginals(World world) {
        List<Block> spawners = new ArrayList<>();
        for (int cx = MapLayout.MIN_CHUNK; cx <= MapLayout.MAX_CHUNK; cx++) {
            for (int cz = MapLayout.MIN_CHUNK; cz <= MapLayout.MAX_CHUNK; cz++) {
                for (var state : world.getChunkAt(cx, cz).getTileEntities()) {
                    if (state instanceof CreatureSpawner spawner && spawner.getSpawnedType() == EntityType.SILVERFISH) {
                        spawners.add(state.getBlock());
                    }
                }
            }
        }
        // 포탈 틀은 스포너 주변(포탈 방 안)에 있음
        for (Block spawner : spawners) {
            for (int dx = -12; dx <= 12; dx++)
                for (int dz = -12; dz <= 12; dz++)
                    for (int dy = -4; dy <= 8; dy++) {
                        Block b = spawner.getRelative(dx, dy, dz);
                        if (b.getType() == Material.END_PORTAL_FRAME) b.setType(Material.STONE_BRICKS, false);
                        else if (b.getType() == Material.END_PORTAL) b.setType(Material.AIR, false);
                    }
            spawner.setType(Material.AIR, false);
        }
    }

    private static void build(World world, Random random) {
        int y0 = FLOOR_Y;

        // 방 껍데기 (석재 벽돌, 이끼/금 간 벽돌 섞음) + 내부 비우기
        for (int x = -HALF_X; x <= HALF_X; x++) {
            for (int z = -HALF_Z; z <= HALF_Z; z++) {
                for (int y = y0; y <= y0 + HEIGHT; y++) {
                    boolean shell = Math.abs(x) == HALF_X || Math.abs(z) == HALF_Z || y == y0 || y == y0 + HEIGHT;
                    set(world, x, y, z, shell ? brick(random) : Material.AIR);
                }
            }
        }
        // 4면 가운데 입구 (요새 통로와 이어질 수 있게)
        for (int y = y0 + 1; y <= y0 + 3; y++) {
            set(world, 0, y, -HALF_Z, Material.AIR);
            set(world, 0, y, HALF_Z, Material.AIR);
            set(world, -HALF_X, y, 0, Material.AIR);
            set(world, HALF_X, y, 0, Material.AIR);
        }

        // 단상 (2칸 높이) + 포탈 아래 용암
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                for (int y = y0 + 1; y <= y0 + 2; y++)
                    set(world, x, y, z, brick(random));
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                set(world, x, y0 + 2, z, Material.LAVA);

        // 포탈 틀 12개 (안쪽을 바라봄, 바닐라처럼 칸마다 10% 확률로 눈 박힘)
        int fy = y0 + 3;
        for (int i = -1; i <= 1; i++) {
            frame(world, i, fy, -2, BlockFace.SOUTH, random);
            frame(world, i, fy, 2, BlockFace.NORTH, random);
            frame(world, -2, fy, i, BlockFace.EAST, random);
            frame(world, 2, fy, i, BlockFace.WEST, random);
        }

        // 남쪽 계단
        for (int x = -1; x <= 1; x++) {
            world.getBlockAt(x, y0 + 1, 4).setBlockData(Material.STONE_BRICK_STAIRS.createBlockData(
                    d -> ((Stairs) d).setFacing(BlockFace.NORTH)), false);
        }

        // 좀벌레 스포너 (북쪽)
        Block spawner = world.getBlockAt(0, y0 + 1, -5);
        spawner.setType(Material.SPAWNER, false);
        if (spawner.getState() instanceof CreatureSpawner cs) {
            cs.setSpawnedType(EntityType.SILVERFISH);
            cs.update(true, false);
        }

        // 조명
        for (int sx : new int[]{-4, 4})
            for (int sz : new int[]{-6, 6})
                set(world, sx, y0 + 1, sz, Material.LANTERN);
    }

    // ───────────── 요새 통로와 연결 ─────────────

    private static final int SEARCH_RADIUS = 64;
    static String lastTunnel = "없음";
    private static final Set<Material> STRONGHOLD_FLOOR = Set.of(
            Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
            Material.INFESTED_STONE_BRICKS, Material.INFESTED_MOSSY_STONE_BRICKS, Material.INFESTED_CRACKED_STONE_BRICKS,
            Material.SMOOTH_STONE_SLAB, Material.STONE_BRICK_SLAB, Material.OAK_PLANKS);

    /** 방 4면 입구 바로 바깥 칸과 바깥 방향. */
    private static final int[][] DOORS = {
            {0, -HALF_Z - 1, 0, -1}, {0, HALF_Z + 1, 0, 1}, {-HALF_X - 1, 0, -1, 0}, {HALF_X + 1, 0, 1, 0}};

    /**
     * 가장 가까운 요새 통로(석재 벽돌 바닥 위 2칸 빈 공간)를 찾아 방 입구에서 거기까지 통로를 뚫음.
     * 높이 차이는 한 칸 전진마다 한 칸씩 계단으로 오르내림.
     */
    static boolean connect(World world, Random random) {
        int minChunk = -SEARCH_RADIUS >> 4, maxChunk = (SEARCH_RADIUS >> 4) - 1;
        int minY = Math.max(world.getMinHeight() + 2, -50), maxY = 60;
        int doorY = FLOOR_Y + 1;

        int[] best = null;
        int[] bestDoor = null;
        int bestCost = Integer.MAX_VALUE;
        for (int cx = minChunk; cx <= maxChunk; cx++) {
            for (int cz = minChunk; cz <= maxChunk; cz++) {
                ChunkSnapshot snap = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = (cx << 4) + lx, z = (cz << 4) + lz;
                        if (Math.abs(x) <= HALF_X + 2 && Math.abs(z) <= HALF_Z + 2) continue; // 방 안/벽
                        for (int y = minY; y <= maxY; y++) {
                            if (!snap.getBlockType(lx, y, lz).isAir() || !snap.getBlockType(lx, y + 1, lz).isAir()) continue;
                            if (!STRONGHOLD_FLOOR.contains(snap.getBlockType(lx, y - 1, lz))) continue;
                            for (int[] door : DOORS) {
                                int horizontal = Math.abs(x - door[0]) + Math.abs(z - door[1]);
                                int dy = Math.abs(y - doorY);
                                if (horizontal < dy) continue; // 계단으로 갈 수 없을 만큼 가파름
                                int cost = horizontal + dy;
                                if (cost < bestCost) {
                                    bestCost = cost;
                                    best = new int[]{x, y, z};
                                    bestDoor = door;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (best == null) return false;
        dig(world, bestDoor, doorY, best, random);
        lastTunnel = "(" + bestDoor[0] + "," + doorY + "," + bestDoor[1] + ") -> (" + best[0] + "," + best[1] + "," + best[2] + ")";
        return true;
    }

    private static void dig(World world, int[] door, int doorY, int[] target, Random random) {
        int x = door[0], y = doorY, z = door[1];
        // 입구 벽 구멍과 이어지는 첫 칸
        carve(world, x, y, z, random);
        // 먼저 입구 바깥 방향으로 한 칸 더 나가서 방 벽에 붙지 않게
        int ox = door[2], oz = door[3];
        if (Math.abs(target[0] - x) + Math.abs(target[2] - z) > Math.abs(target[1] - y)) {
            x += ox;
            z += oz;
            carve(world, x, y, z, random);
        }

        int guard = 0;
        while ((x != target[0] || z != target[2] || y != target[1]) && guard++ < 512) {
            int px = x, pz = z, py = y;
            int stepX = 0, stepZ = 0;
            if (x != target[0]) stepX = Integer.signum(target[0] - x);
            else if (z != target[2]) stepZ = Integer.signum(target[2] - z);
            x += stepX;
            z += stepZ;
            if (y != target[1]) y += Integer.signum(target[1] - y);
            carve(world, x, y, z, random);

            BlockFace dir = stepX > 0 ? BlockFace.EAST : stepX < 0 ? BlockFace.WEST : stepZ > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
            if (y > py) {
                // 올라감: 새 칸의 바닥을 진행 방향으로 오르는 계단으로
                stairs(world, x, y - 1, z, dir);
                world.getBlockAt(px, py + 3, pz).setType(Material.AIR, false); // 머리 공간
            } else if (y < py) {
                // 내려감: 이전 칸 바닥을 반대 방향으로 오르는 계단으로
                stairs(world, px, py - 1, pz, dir.getOppositeFace());
                world.getBlockAt(x, y + 3, z).setType(Material.AIR, false);
            }
        }
    }

    /** 한 칸: 3칸 높이 비우고, 바닥은 벽돌, 옆/천장의 막힌 곳(물·용암 포함)은 벽돌로 둘러쌈. */
    private static void carve(World world, int x, int y, int z, Random random) {
        for (int dy = 0; dy < 3; dy++) world.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
        Block floor = world.getBlockAt(x, y - 1, z);
        if (!floor.getType().name().endsWith("_STAIRS")) floor.setType(brick(random), false);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    if (dx == 0 && dz == 0 && dy >= 0 && dy < 3) continue;
                    Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    Material t = b.getType();
                    // 빈 공간(다른 통로·동굴)은 그대로 둬서 자연스럽게 이어지게
                    if (t.isAir() || t.name().endsWith("_STAIRS") || STRONGHOLD_FLOOR.contains(t)) continue;
                    if (t == Material.END_PORTAL_FRAME || t == Material.SPAWNER || t == Material.LANTERN) continue;
                    b.setType(brick(random), false);
                }
            }
        }
    }

    private static void stairs(World world, int x, int y, int z, BlockFace facing) {
        world.getBlockAt(x, y, z).setBlockData(Material.STONE_BRICK_STAIRS.createBlockData(
                d -> ((Stairs) d).setFacing(facing)), false);
    }

    private static void frame(World world, int x, int y, int z, BlockFace facing, Random random) {
        world.getBlockAt(x, y, z).setBlockData(Material.END_PORTAL_FRAME.createBlockData(d -> {
            EndPortalFrame f = (EndPortalFrame) d;
            f.setFacing(facing);
            f.setEye(random.nextFloat() < 0.1f);
        }), false);
    }

    private static Material brick(Random random) {
        float r = random.nextFloat();
        if (r < 0.15f) return Material.MOSSY_STONE_BRICKS;
        if (r < 0.3f) return Material.CRACKED_STONE_BRICKS;
        return Material.STONE_BRICKS;
    }

    private static void set(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
