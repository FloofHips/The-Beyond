package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.block.ThornsBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class ThornsFeature extends Feature<NoneFeatureConfiguration> {
    public ThornsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();

        return placeThorns(blockpos, worldgenlevel, randomsource, new ArrayList<>());
    }

    /** {@code thornsPos} is per call: one Feature serves every placement and, under parallel generation, several threads. */
    public abstract boolean placeThorns(BlockPos blockpos, WorldGenLevel worldgenlevel, RandomSource randomsource, List<BlockPos> thornsPos);


    public void placeThorn(WorldGenLevel level, BlockPos pos, List<BlockPos> thornsPos) {
        if (!level.isEmptyBlock(pos)) return;
        this.setBlock(level, pos, getBlockState());
        thornsPos.add(pos);
    }

    public abstract @NotNull BlockState getBlockState();

    public void cleanUpBlockstates(WorldGenLevel level, List<BlockPos> thornsPos) {
        List<BlockPos> currentThorns = new ArrayList<>(thornsPos);
        for (BlockPos pos : currentThorns) {
            level.setBlock(pos, ThornsBlock.getStateWithConnections(level, pos, getBlockState()), 3);
        }
        thornsPos.clear();
    }

    public abstract @NotNull BlockState getFloorBlock();


    public void placeBranch(WorldGenLevel level, RandomSource randomsource, BlockPos pos, List<BlockPos> thornsPos) {
        if (randomsource.nextInt(4) == 0) {
            placeThorn(level, pos, thornsPos);
            //if (randomsource.nextBoolean() && level.isEmptyBlock(pos.below())) {
            //    level.setBlock(pos.below(), getFloorBlock(),3);
            //}
        }
    }
}
