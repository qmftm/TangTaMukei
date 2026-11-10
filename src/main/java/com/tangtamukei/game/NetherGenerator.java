package com.tangtamukei.game;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** 맵 범위(16x16 청크) 안에서만 바닐라 네더를 생성하고 나머지는 공허. */
public final class NetherGenerator extends ChunkGenerator {

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

    /** 요새·보루는 GameManager 가 정해진 위치에 직접 놓음. */
    @Override
    public boolean shouldGenerateStructures(WorldInfo info, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }
}
