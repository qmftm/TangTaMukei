package com.tangtamukei.game;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 맵 범위(16x16 청크) 안에서만 바닐라 네더를 생성하고 나머지는 공허.
 * 바이옴은 {@link NetherLayout} 의 칸 배치를 따름 (겉흙·식물·현무암 등은 바이옴별로 바닐라가 생성).
 */
public final class NetherGenerator extends ChunkGenerator {

    private final NetherLayout layout;

    public NetherGenerator(NetherLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean shouldGenerateNoise(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return MapLayout.insideChunk(chunkX, chunkZ);
    }

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

    /** 요새·보루 등은 GameManager 가 정해진 위치에 직접 놓음. */
    @Override
    public boolean shouldGenerateStructures(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return layout.nearest(x, z).biome;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                List<Biome> list = new ArrayList<>();
                for (NetherCell c : NetherCell.values()) if (!list.contains(c.biome)) list.add(c.biome);
                return list;
            }
        };
    }
}
