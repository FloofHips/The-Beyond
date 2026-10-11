package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BlindingThornsFeature extends ThornsFeature {
    public BlindingThornsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    public boolean placeThorns(BlockPos pos, WorldGenLevel level, RandomSource randomsource, List<BlockPos> thornsPos) {
        if (level.isEmptyBlock(pos)) {
            for (Direction d : Direction.values()) {
                if (d.getAxis().isVertical()) continue;
                placeThorn(level, pos.offset(d.getStepX(), 0,d.getStepZ()), thornsPos);
                //level.setBlock(pos.offset(d.getStepX(), -1,d.getStepZ()), getFloorBlock(), 3);
            }

            //level.setBlock(pos.offset(0, -1,0), getFloorBlock(), 3);
            for (int i = 0; i < randomsource.nextInt(5, 20); i++) {
                placeThorn(level, pos.offset(0, i, 0), thornsPos);
                if (randomsource.nextFloat() < 0.3f) {
                    placeBranch(level, randomsource, pos.offset(1, i, 0), thornsPos);
                    placeBranch(level, randomsource, pos.offset(-1, i, 0), thornsPos);
                    placeBranch(level, randomsource, pos.offset(0, i, -1), thornsPos);
                    placeBranch(level, randomsource, pos.offset(0, i, 1), thornsPos);
                }
            }
        }
        cleanUpBlockstates(level, thornsPos);
        return true;
    }

    @Override
    public @NotNull BlockState getBlockState() {
        return BeyondBlocks.BLINDING_THORN.get().defaultBlockState();
    }

    @Override
    public @NotNull BlockState getFloorBlock() {
        return BeyondBlocks.SOOT_BLOCK.get().defaultBlockState();
    }
}
