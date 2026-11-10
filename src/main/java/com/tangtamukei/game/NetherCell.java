package com.tangtamukei.game;

import org.bukkit.block.Biome;
import org.bukkit.generator.structure.Structure;

/** 네더 맵의 한 칸 종류. 지형은 바닐라 네더 그대로, 바이옴과 구조물만 칸별로 지정. */
public enum NetherCell {
    WASTES      (Biome.NETHER_WASTES,    null,                          false),
    RUINED      (Biome.NETHER_WASTES,    Structure.RUINED_PORTAL_NETHER, false),
    CRIMSON     (Biome.CRIMSON_FOREST,   null,                          false),
    SOUL_SAND   (Biome.SOUL_SAND_VALLEY, Structure.NETHER_FOSSIL,       false),
    BASALT      (Biome.BASALT_DELTAS,    null,                          false),

    // ── 4x4 청크 ──
    BASTION     (Biome.CRIMSON_FOREST,   Structure.BASTION_REMNANT,     true),
    BIG_BASALT  (Biome.BASALT_DELTAS,    null,                          true),
    BIG_SOUL    (Biome.SOUL_SAND_VALLEY, Structure.NETHER_FOSSIL,       true),

    // ── 중앙: 뒤틀린 숲 + 네더 요새 ──
    CENTER      (Biome.WARPED_FOREST,    Structure.FORTRESS,            true);

    public final Biome biome;
    public final Structure structure;
    public final boolean big;

    NetherCell(Biome biome, Structure structure, boolean big) {
        this.biome = biome;
        this.structure = structure;
        this.big = big;
    }
}
