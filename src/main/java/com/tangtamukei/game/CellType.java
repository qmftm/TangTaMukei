package com.tangtamukei.game;

import org.bukkit.block.Biome;
import org.bukkit.generator.structure.Structure;

import java.util.List;

/**
 * 맵 한 칸(2x2 청크) 자리 종류. 각 칸은 숨겨진 야생 월드에서 조건에 맞는 곳을 찾아 그대로 복사해 옴.
 * 변형(Variant)에 구조물이 있으면 그 구조물을, 없으면 그 바이옴을 찾음.
 * - 구조물 자리: 구조물이 잘리지 않게 2x2 청크(큰 구조물은 4x4)를 한 곳에서 이어서 가져옴
 * - 그 외 자리: 청크마다 각각 다른 곳에서 가져와서 청크마다 높낮이가 다르게 끊겨 보임
 * - 야생 자리(WILD_*)는 청크마다 바이옴도 다름
 * 변형은 사분면마다 다르게 (게임마다 섞임), 큰 구조물은 게임마다 하나
 */
public enum CellType {

    SPAWN(v(Biome.PLAINS), v(Biome.SUNFLOWER_PLAINS), v(Biome.MEADOW)),
    // 야생(나무): 스폰 옆이라 나무가 있는 바이옴만
    WILD_FOREST(v(Biome.FOREST), v(Biome.BIRCH_FOREST), v(Biome.FLOWER_FOREST), v(Biome.DAPPLED_FOREST),
            v(Biome.OLD_GROWTH_BIRCH_FOREST), v(Biome.DARK_FOREST), v(Biome.TAIGA),
            v(Biome.OLD_GROWTH_SPRUCE_TAIGA), v(Biome.WINDSWEPT_FOREST)),
    // 야생(혼합): 벚꽃 숲 2청크 + 대나무 숲 1청크는 항상, 나머지 1청크는 랜덤
    WILD_MIXED(v(Biome.CHERRY_GROVE), v(Biome.BAMBOO_JUNGLE),
            v(Biome.MEADOW), v(Biome.GROVE), v(Biome.SNOWY_PLAINS), v(Biome.ICE_SPIKES), v(Biome.WINDSWEPT_HILLS),
            v(Biome.STONY_PEAKS), v(Biome.SNOWY_SLOPES), v(Biome.SAVANNA_PLATEAU), v(Biome.WINDSWEPT_GRAVELLY_HILLS),
            v(Biome.JAGGED_PEAKS), v(Biome.SPARSE_JUNGLE)),
    TAIGA(s(Structure.TRAIL_RUINS), s(Structure.IGLOO)),
    BADLANDS(v(Biome.BADLANDS), v(Biome.WOODED_BADLANDS), v(Biome.ERODED_BADLANDS)),
    DESERT(s(Structure.DESERT_PYRAMID)),
    OUTPOST(s(Structure.PILLAGER_OUTPOST)),
    JUNGLE(s(Structure.JUNGLE_PYRAMID)),
    SWAMP(s(Structure.SWAMP_HUT)),
    OCEAN_RUINS(s(Structure.OCEAN_RUIN_WARM), s(Structure.OCEAN_RUIN_COLD)),
    SHIPWRECK(s(Structure.SHIPWRECK)),

    // ── 4x4 청크 큰 구조물: 게임마다 변형 하나 ──
    VILLAGE(s(Structure.VILLAGE_PLAINS), s(Structure.VILLAGE_SAVANNA), s(Structure.VILLAGE_TAIGA),
            s(Structure.VILLAGE_SNOWY), s(Structure.VILLAGE_DESERT)),
    MANSION(s(Structure.MANSION)),
    ANCIENT_CITY(s(Structure.ANCIENT_CITY)),
    MONUMENT(s(Structure.MONUMENT)),
    // 중앙: 요새 엔드 포탈 방이 정중앙에 오도록 복사
    STRONGHOLD(s(Structure.STRONGHOLD));

    public final List<Variant> variants;

    CellType(Variant... variants) {
        this.variants = List.of(variants);
    }

    /** 찾을 대상: 구조물이 있으면 구조물, 없으면 바이옴. */
    public record Variant(Biome biome, Structure structure) {
    }

    private static Variant v(Biome biome) {
        return new Variant(biome, null);
    }

    private static Variant s(Structure structure) {
        return new Variant(null, structure);
    }

    public boolean isWild() {
        return this == WILD_FOREST || this == WILD_MIXED;
    }

    public boolean isBig() {
        return this == VILLAGE || this == MANSION || this == ANCIENT_CITY || this == MONUMENT || this == STRONGHOLD;
    }
}
