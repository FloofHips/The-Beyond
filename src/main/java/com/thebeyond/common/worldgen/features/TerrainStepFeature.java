package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.worldgen.BeyondEndBiomeSource;
import com.thebeyond.util.VoronoiNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class TerrainStepFeature  extends Feature<NoneFeatureConfiguration>  {
    public TerrainStepFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        RandomSource randomsource = context.random();

        BeyondEndBiomeSource source = (BeyondEndBiomeSource) context.chunkGenerator().getBiomeSource();
        VoronoiNoise.CellResult3D cell = source.getCell(origin.getX(), origin.getY(), origin.getZ());

        double distance = new Vec3(cell.cellX(), cell.cellY(), cell.cellZ()).subtract(origin.getCenter()).length();
        double t = Mth.clamp(distance / 200.0, 0.0, 1.0);

        //this is to make it so features in the same subchunk or nearby subchunks start at the same y origin
        //to create the illusion of continuity, or else they'll be bound to 3x3 chunks and look kind of bad
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int groupX = Math.floorDiv(chunkX, 3);
        int groupZ = Math.floorDiv(chunkZ, 3);
        long localChunkSeed = groupX * 31L + groupZ * 17L;
        RandomSource chunkRandom = RandomSource.create(localChunkSeed);

        int yOffset = chunkRandom.nextInt(-5, 5);
        int fixedY = (int) (yOffset + (cell.cellY()/4)*4);
        //guy that takes care of the y level ends here

        //do not make the max bigger than 13 or else there will be issues (13 + 13 + noise addon is usually just shy enough of 3x3 chunks)
        int radius = level.getRandom().nextInt(7, 13);
        int height = (int) Mth.lerp(t, 13, 7);
        SimplexNoise noise = new SimplexNoise(randomsource);

        for(int j = -radius; j <= radius; ++j) {
            for(int k = -radius; k <= radius; ++k) {
                for(int i = 0; i >= -height ; i--) {

                    BlockPos blockPos = new BlockPos(origin.getX() + j, fixedY + i, origin.getZ() + k);
                    double noiseValue = noise.getValue(blockPos.getX() * 0.05,blockPos.getY() * 0.01, blockPos.getZ() * 0.05);
                    float r = radius + i;
                    double noisyRadius = Math.max(r + (noiseValue * 2), 0);
                    double voidRadius = Math.max(r + (noiseValue * 2)-2, 0);

                    if (r <= 0) break;
                    if ((float)(j * j + k * k) <= noisyRadius * noisyRadius) {
                        if ((float)(j * j + k * k) <= voidRadius * voidRadius && i > -radius+2) {
                            this.setBlock(level, blockPos, getState(t));
                        }
                        else if (!level.getBlockState(blockPos).is(BlockTags.WOOL)) {
                            this.setBlock(level, blockPos, Blocks.END_STONE.defaultBlockState());
                        }
                    }
                }
            }
        }

        return true;
    }

    private static final Block[] WOOLS = {
            Blocks.BLACK_WOOL,
            Blocks.BROWN_WOOL,
            Blocks.RED_WOOL,
            Blocks.ORANGE_WOOL,
            Blocks.YELLOW_WOOL,
            Blocks.LIME_WOOL,
            Blocks.GREEN_WOOL,
            Blocks.CYAN_WOOL,
            Blocks.LIGHT_BLUE_WOOL,
            Blocks.BLUE_WOOL,
            Blocks.PURPLE_WOOL,
            Blocks.MAGENTA_WOOL,
            Blocks.PINK_WOOL,
            Blocks.WHITE_WOOL
    };

    private static @NotNull BlockState getState(double distance) {
        int index = (int) Math.round(distance * (WOOLS.length - 1));
        return WOOLS[index].defaultBlockState();
    }
}