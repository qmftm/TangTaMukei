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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
