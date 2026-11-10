package com.tangtamukei.game;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 게임 오버월드 생성기: 맵 안 청크는 {@link PastedOverworld} 가 찾아둔 소스 지형을 블록 그대로 복사,
 * 맵 밖은 완전한 공허. 바닐라 생성(지형·동굴·나무·구조물)은 전혀 하지 않음 (이미 소스에 다 있음).
 */
public final class PasteGenerator extends ChunkGenerator {

    private final PastedOverworld paste;

    public PasteGenerator(PastedOverworld paste) {
        this.paste = paste;
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        if (!MapLayout.insideChunk(chunkX, chunkZ)) return;
        int minY = info.getMinHeight(), maxY = info.getMaxHeight();
        PastedOverworld.Region r = paste.regionAt(chunkX << 4, chunkZ << 4);
        int offX = r.srcX() - r.targetX(), offZ = r.srcZ() - r.targetZ();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int sx = (chunkX << 4) + lx + offX, sz = (chunkZ << 4) + lz + offZ;
                ChunkSnapshot snap = paste.snapshot(sx, sz);
                if (snap == null) continue;
                int bx = sx & 15, bz = sz & 15;
                int top = Math.min(maxY - 1, snap.getHighestBlockYAt(bx, bz) + 1);
                for (int y = minY; y <= top; y++) {
                    BlockData bd = snap.getBlockData(bx, y, bz);
                    if (!bd.getMaterial().isAir()) data.setBlock(lx, y, lz, bd);
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        List<Biome> list = new ArrayList<>(paste.biomes());
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                PastedOverworld.Region r = paste.regionAt(x, z);
                int cx = Math.clamp(x, MapLayout.MIN, MapLayout.MAX - 1), cz = Math.clamp(z, MapLayout.MIN, MapLayout.MAX - 1);
                Biome b = paste.sourceBiome(cx - r.targetX() + r.srcX(), y, cz - r.targetZ() + r.srcZ());
                return list.contains(b) ? b : list.getFirst();
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return list;
            }
        };
    }
}
