package com.tangtamukei.game;

import org.bukkit.block.Biome;
import org.bukkit.generator.structure.Structure;

/**
 * 맵의 한 칸(2x2 청크) 종류.
 * 지형 높이/굴곡, 바이옴, 그 칸에 놓을 바닐라 구조물을 정의.
 */
public enum CellType {

    //            바이옴                 지하 바이옴       높이  굴곡  구조물
    SPAWN        (Biome.PLAINS,         null,             67,   2,   null),
    FOREST       (Biome.FOREST,         null,             70,   5,   null),
    TAIGA        (Biome.TAIGA,          null,             72,   6,   null),
    CHERRY_GROVE (Biome.CHERRY_GROVE,   null,             86,  12,   null),
    BADLANDS     (Biome.BADLANDS,       null,             78,  10,   Structure.MINESHAFT_MESA),
    DESERT       (Biome.DESERT,         null,             66,   3,   Structure.DESERT_PYRAMID),
    OUTPOST      (Biome.SAVANNA,        null,             70,   3,   Structure.PILLAGER_OUTPOST),
    BAMBOO       (Biome.BAMBOO_JUNGLE,  null,             70,   6,   Structure.JUNGLE_PYRAMID),
    SWAMP        (Biome.SWAMP,          null,             62,   2,   Structure.SWAMP_HUT),
    OCEAN_RUINS  (Biome.LUKEWARM_OCEAN, null,             42,   4,   Structure.OCEAN_RUIN_WARM),
    SHIPWRECK    (Biome.OCEAN,          null,             42,   4,   Structure.SHIPWRECK),

    // ── 4x4 청크 (2x2 칸) 큰 구조물 ──
    VILLAGE      (Biome.PLAINS,         null,             67,   1,   Structure.VILLAGE_PLAINS),
    MANSION      (Biome.DARK_FOREST,    null,             70,   3,   Structure.MANSION),
    ANCIENT_CITY (Biome.SNOWY_TAIGA,    Biome.DEEP_DARK,  74,   5,   Structure.ANCIENT_CITY),
    MONUMENT     (Biome.DEEP_OCEAN,     null,             30,   2,   Structure.MONUMENT),

    // ── 중앙 ──
    STRONGHOLD   (Biome.MEADOW,         null,             70,   3,   Structure.STRONGHOLD);

    public final Biome biome;
    public final Biome undergroundBiome; // y < 0 에서 쓸 바이옴 (없으면 biome)
    public final int height;
    public final int roughness;
    public final Structure structure;

    CellType(Biome biome, Biome undergroundBiome, int height, int roughness, Structure structure) {
        this.biome = biome;
        this.undergroundBiome = undergroundBiome;
        this.height = height;
        this.roughness = roughness;
        this.structure = structure;
    }

    /** 바다 칸: 지형이 항상 해수면 아래. */
    public boolean isWater() {
        return height < OverworldGenerator.SEA_LEVEL;
    }

    public boolean isBig() {
        return this == VILLAGE || this == MANSION || this == ANCIENT_CITY || this == MONUMENT || this == STRONGHOLD;
    }
}
