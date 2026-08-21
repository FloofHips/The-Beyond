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

public abstract class ThornsFeature  extends Feature<NoneFeatureConfiguration> {
    List<BlockPos> thornsPos = new ArrayList<>();

    public ThornsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();

        return placeThorns(blockpos, worldgenlevel, randomsource);
    }

    public abstract boolean placeThorns(BlockPos blockpos, WorldGenLevel worldgenlevel, RandomSource randomsource);


    public void placeThorn(WorldGenLevel level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) return;
        this.setBlock(level, pos, getBlockState());
        thornsPos.add(pos);
    }

    public abstract @NotNull BlockState getBlockState();

    public void cleanUpBlockstates(WorldGenLevel level) {
        List<BlockPos> currentThorns = new ArrayList<>(thornsPos);
        for (BlockPos pos : currentThorns) {
            level.setBlock(pos, ThornsBlock.getStateWithConnections(level, pos, getBlockState()), 3);
        }
        thornsPos.clear();
    }

    public abstract @NotNull BlockState getFloorBlock();


    public void placeBranch(WorldGenLevel level, RandomSource randomsource, BlockPos pos) {
        if (randomsource.nextInt(4) == 0) {
            placeThorn(level, pos);
            if (randomsource.nextBoolean() && level.isEmptyBlock(pos.below())) {
                level.setBlock(pos.below(), getFloorBlock(),3);
            }
        }
    }
}
