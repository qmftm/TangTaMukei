package com.tangtamukei.game;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 게임 오버월드 생성기.
 * 지형은 칸 종류별 높이를 부드럽게 이어 붙여 직접 만들고,
 * 겉흙(잔디/모래/테라코타), 동굴, 광석, 나무 등은 바닐라에게 맡깁니다.
 * 맵 밖은 완전한 공허.
 */
public final class OverworldGenerator extends ChunkGenerator {

    public static final int SEA_LEVEL = 62;
    private static final int BEDROCK_Y = -64;

    private final MapLayout layout;
    private final SimplexOctaveGenerator noise;
    private final SimplexOctaveGenerator detail;

    public OverworldGenerator(MapLayout layout, long seed) {
        this.layout = layout;
        this.noise = new SimplexOctaveGenerator(seed, 4);
        this.noise.setScale(1 / 48.0);
        this.detail = new SimplexOctaveGenerator(seed ^ 0x5DEECE66DL, 2);
        this.detail.setScale(1 / 8.0);
    }

    // ───────────── 지형 높이 ─────────────

    /** (x, z) 의 지표면 높이 (가장 위 고체 블록의 y). */
    public int surfaceY(int x, int z) {
        // 칸 중심 4개의 높이/굴곡을 부드럽게 보간
        double u = (x - MapLayout.MIN) / (double) MapLayout.CELL - 0.5;
        double v = (z - MapLayout.MIN) / (double) MapLayout.CELL - 0.5;
        int c0 = (int) Math.floor(u), r0 = (int) Math.floor(v);
        double fu = smooth(u - c0), fv = smooth(v - r0);

        double base = lerp(fv,
                lerp(fu, layout.height(c0, r0), layout.height(c0 + 1, r0)),
                lerp(fu, layout.height(c0, r0 + 1), layout.height(c0 + 1, r0 + 1)));
        double rough = lerp(fv,
                lerp(fu, layout.roughness(c0, r0), layout.roughness(c0 + 1, r0)),
                lerp(fu, layout.roughness(c0, r0 + 1), layout.roughness(c0 + 1, r0 + 1)));

        double n = noise.noise(x, z, 0.5, 0.5, true);
        int y = (int) Math.round(base + n * rough);

        // 해안 정리: 육지 칸은 항상 물 위, 바다 칸은 항상 물 아래 (경계는 얕은 턱)
        CellType own = layout.nearest(x, z);
        if (own.isWater()) return Math.min(y, SEA_LEVEL - 2);
        if (own == CellType.SWAMP) return Math.max(y, SEA_LEVEL - 2); // 늪은 물웅덩이 허용
        return Math.max(y, SEA_LEVEL + 1);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    // ───────────── 블록 채우기 ─────────────

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        if (!MapLayout.insideChunk(chunkX, chunkZ)) return; // 공허

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunkX << 4) + lx, z = (chunkZ << 4) + lz;
                int top = surfaceY(x, z);

                // y -64: 배드락 경계
                data.setBlock(lx, BEDROCK_Y, lz, Material.BEDROCK);

                // 심층암 → 돌 (경계는 살짝 들쭉날쭉)
                int deepslateTop = (int) Math.round(detail.noise(x, z, 0.5, 0.5, true) * 3);
                data.setRegion(lx, BEDROCK_Y + 1, lz, lx + 1, Math.min(deepslateTop, top) + 1, lz + 1, Material.DEEPSLATE);
                if (top > deepslateTop) {
                    data.setRegion(lx, deepslateTop + 1, lz, lx + 1, top + 1, lz + 1, Material.STONE);
                }
                // 바다
                if (top < SEA_LEVEL) {
                    data.setRegion(lx, top + 1, lz, lx + 1, SEA_LEVEL + 1, lz + 1, Material.WATER);
                }
            }
        }
    }

    @Override
    public int getBaseHeight(WorldInfo info, Random random, int x, int z, HeightMap heightMap) {
        if (!MapLayout.inside(x, z)) return info.getMinHeight();
        int top = surfaceY(x, z);
        return switch (heightMap) {
            case OCEAN_FLOOR, OCEAN_FLOOR_WG -> top + 1;
            default -> Math.max(top, SEA_LEVEL) + 1;
        };
    }

    // 맵 안에서만 바닐라 겉흙/동굴/장식(광석, 나무)/동물 생성
    @Override
    public boolean shouldGenerateSurface(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return MapLayout.insideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateCaves(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return MapLayout.insideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateDecorations(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return MapLayout.insideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateMobs(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return MapLayout.insideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateNoise(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return false;
    }

    /** 구조물은 GameManager 가 정해진 위치에 직접 놓음. */
    @Override
    public boolean shouldGenerateStructures(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, surfaceY(0, 0) + 1, 0.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                CellType type = layout.nearest(x, z);
                if (type.undergroundBiome != null && y < type.undergroundBelow()) return type.undergroundBiome;
                return layout.nearestVariant(x, z).biome();
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                List<Biome> list = new ArrayList<>();
                for (CellType t : CellType.values()) {
                    for (CellType.Variant v : t.variants) if (!list.contains(v.biome())) list.add(v.biome());
                    if (t.undergroundBiome != null && !list.contains(t.undergroundBiome)) list.add(t.undergroundBiome);
                }
                return list;
            }
        };
    }
}
