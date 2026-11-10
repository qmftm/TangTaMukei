package com.tangtamukei.game;

import org.bukkit.block.Biome;
import org.bukkit.generator.structure.Structure;

import java.util.List;

/**
 * 맵의 한 칸(2x2 청크) 자리 종류.
 * 구조물 자리: 비슷한 계열의 바이옴 변형 중 사분면마다 다른 변형이 나옴 (게임마다 섞임).
 *             큰 구조물 자리는 게임마다 변형 하나.
 * 야생 자리(WILD_*): 구조물 없는 일반 바이옴 칸. 4개 청크가 각각 다른 바이옴 (1청크 단위).
 */
public enum CellType {

    //            높이 굴곡 지하 바이옴 (y < 30)        변형들
    SPAWN        (67,  2, null,
            v(Biome.PLAINS), v(Biome.SUNFLOWER_PLAINS), v(Biome.MEADOW, null, 2, 1)),
    // 야생(나무): 스폰 옆이라 나무가 있는 바이옴만. 청크마다 다른 바이옴
    WILD_FOREST  (70,  5, null,
            v(Biome.FOREST), v(Biome.BIRCH_FOREST), v(Biome.FLOWER_FOREST), v(Biome.DAPPLED_FOREST),
            v(Biome.OLD_GROWTH_BIRCH_FOREST, null, 2, 0), v(Biome.DARK_FOREST),
            v(Biome.TAIGA, null, 2, 1), v(Biome.OLD_GROWTH_SPRUCE_TAIGA, null, 3, 1), v(Biome.WINDSWEPT_FOREST, null, 10, 6)),
    // 야생(혼합): 벚꽃 숲 2청크는 항상 + 산·설원 등 2청크
    WILD_MIXED   (72,  5, Biome.LUSH_CAVES,
            v(Biome.CHERRY_GROVE, null, 14, 7),
            v(Biome.MEADOW, null, 6, 0), v(Biome.GROVE, null, 12, 3), v(Biome.SNOWY_PLAINS, null, -3, -2),
            v(Biome.ICE_SPIKES, null, -3, -2), v(Biome.WINDSWEPT_HILLS, null, 14, 9), v(Biome.STONY_PEAKS, null, 22, 10),
            v(Biome.SNOWY_SLOPES, null, 18, 6), v(Biome.SUNFLOWER_PLAINS, null, -4, -2), v(Biome.SAVANNA, null, -2, -2),
            v(Biome.SPARSE_JUNGLE, null, 0, 0), v(Biome.WINDSWEPT_GRAVELLY_HILLS, null, 12, 8)),
    TAIGA        (72,  6, null,
            v(Biome.TAIGA, Structure.TRAIL_RUINS, 0, 0),
            v(Biome.OLD_GROWTH_SPRUCE_TAIGA, Structure.TRAIL_RUINS, 2, 0),
            v(Biome.OLD_GROWTH_PINE_TAIGA, Structure.TRAIL_RUINS, 2, 0),
            v(Biome.SNOWY_TAIGA, Structure.IGLOO, 0, 0)),
    BADLANDS     (78, 10, Biome.DRIPSTONE_CAVES,
            v(Biome.BADLANDS, Structure.MINESHAFT_MESA),
            v(Biome.WOODED_BADLANDS, Structure.MINESHAFT_MESA, 4, 0),
            v(Biome.ERODED_BADLANDS, Structure.MINESHAFT_MESA, 0, 6)),
    DESERT       (66,  3, Biome.DRIPSTONE_CAVES,
            v(Biome.DESERT, Structure.DESERT_PYRAMID)),
    OUTPOST      (70,  3, null,
            v(Biome.SAVANNA, Structure.PILLAGER_OUTPOST),
            v(Biome.SAVANNA_PLATEAU, Structure.PILLAGER_OUTPOST, 8, 0),
            v(Biome.WINDSWEPT_SAVANNA, Structure.PILLAGER_OUTPOST, 4, 5)),
    BAMBOO       (70,  6, Biome.LUSH_CAVES,
            v(Biome.BAMBOO_JUNGLE, Structure.JUNGLE_PYRAMID),
            v(Biome.JUNGLE, Structure.JUNGLE_PYRAMID),
            v(Biome.SPARSE_JUNGLE, Structure.JUNGLE_PYRAMID)),
    SWAMP        (62,  2, null,
            v(Biome.SWAMP, Structure.SWAMP_HUT), v(Biome.MANGROVE_SWAMP, Structure.SWAMP_HUT)),
    OCEAN_RUINS  (42,  4, null,
            v(Biome.LUKEWARM_OCEAN, Structure.OCEAN_RUIN_WARM), v(Biome.WARM_OCEAN, Structure.OCEAN_RUIN_WARM),
            v(Biome.COLD_OCEAN, Structure.OCEAN_RUIN_COLD), v(Biome.OCEAN, Structure.OCEAN_RUIN_COLD)),
    SHIPWRECK    (42,  4, null,
            v(Biome.OCEAN, Structure.SHIPWRECK), v(Biome.WARM_OCEAN, Structure.SHIPWRECK),
            v(Biome.COLD_OCEAN, Structure.SHIPWRECK), v(Biome.LUKEWARM_OCEAN, Structure.SHIPWRECK)),

    // ── 4x4 청크 (2x2 칸) 큰 구조물: 게임마다 변형 하나 ──
    VILLAGE      (67,  1, null,
            v(Biome.PLAINS, Structure.VILLAGE_PLAINS), v(Biome.SAVANNA, Structure.VILLAGE_SAVANNA),
            v(Biome.TAIGA, Structure.VILLAGE_TAIGA), v(Biome.SNOWY_PLAINS, Structure.VILLAGE_SNOWY),
            v(Biome.DESERT, Structure.VILLAGE_DESERT)),
    MANSION      (70,  3, null,
            v(Biome.DARK_FOREST, Structure.MANSION), v(Biome.PALE_GARDEN, Structure.MANSION)),
    ANCIENT_CITY (74,  5, Biome.DEEP_DARK,
            v(Biome.SNOWY_TAIGA, Structure.ANCIENT_CITY), v(Biome.GROVE, Structure.ANCIENT_CITY, 4, 2),
            v(Biome.ICE_SPIKES, Structure.ANCIENT_CITY)),
    MONUMENT     (30,  2, null,
            v(Biome.DEEP_OCEAN, Structure.MONUMENT), v(Biome.DEEP_LUKEWARM_OCEAN, Structure.MONUMENT),
            v(Biome.DEEP_COLD_OCEAN, Structure.MONUMENT)),

    // ── 중앙 ──
    STRONGHOLD   (70,  3, null,
            v(Biome.MEADOW, Structure.STRONGHOLD), v(Biome.PLAINS, Structure.STRONGHOLD),
            v(Biome.FLOWER_FOREST, Structure.STRONGHOLD));

    /** 지하 바이옴이 시작되는 높이 (고대 도시의 깊은 어둠은 0). */
    public static final int CAVE_BIOME_Y = 30;

    public final int height;
    public final int roughness;
    public final Biome undergroundBiome;
    public final List<Variant> variants;

    CellType(int height, int roughness, Biome undergroundBiome, Variant... variants) {
        this.height = height;
        this.roughness = roughness;
        this.undergroundBiome = undergroundBiome;
        this.variants = List.of(variants);
    }

    /** 바이옴 변형: 바이옴, 구조물, 기본 높이/굴곡에 더할 값. */
    public record Variant(Biome biome, Structure structure, int heightOffset, int roughnessOffset) {
    }

    private static Variant v(Biome biome) {
        return new Variant(biome, null, 0, 0);
    }

    private static Variant v(Biome biome, Structure structure) {
        return new Variant(biome, structure, 0, 0);
    }

    private static Variant v(Biome biome, Structure structure, int dh, int dr) {
        return new Variant(biome, structure, dh, dr);
    }

    public boolean isWild() {
        return this == WILD_FOREST || this == WILD_MIXED;
    }

    public int undergroundBelow() {
        return this == ANCIENT_CITY ? 0 : CAVE_BIOME_Y;
    }

    /** 바다 칸: 지형이 항상 해수면 아래. */
    public boolean isWater() {
        return height < OverworldGenerator.SEA_LEVEL;
    }

    public boolean isBig() {
        return this == VILLAGE || this == MANSION || this == ANCIENT_CITY || this == MONUMENT || this == STRONGHOLD;
    }
}
