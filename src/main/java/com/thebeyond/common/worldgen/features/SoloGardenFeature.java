package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.block.MirrorBlock;
import com.thebeyond.common.block.RakedNacreBlock;
import com.thebeyond.common.block.blockstates.RakedProperty;
import com.thebeyond.common.entity.BaubleEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class SoloGardenFeature extends Feature<NoneFeatureConfiguration> {
    public SoloGardenFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();

        if (worldgenlevel.isEmptyBlock(blockpos) && !worldgenlevel.isEmptyBlock(blockpos.below())) {

            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    BlockPos offset = blockpos.below().offset(i, 0, j);
                    if (worldgenlevel.getBlockState(offset).is(BeyondTags.END_DECORATOR_REPLACEABLE) || worldgenlevel.getBlockState(offset).isAir()) {
                        if ((Mth.abs(i)!=3 && Mth.abs(j)!=3)) {
                            worldgenlevel.setBlock(offset, getBlockState(i, j, randomsource.nextInt(6)), 3);
                            worldgenlevel.setBlock(offset.above(), Blocks.AIR.defaultBlockState(), 3);
                            if (worldgenlevel.getBlockState(offset.below()).isAir()) worldgenlevel.setBlock(offset.below(), Blocks.END_STONE.defaultBlockState(), 3);
                        }
                        else {
                            if (((Mth.abs(i)<2) || (Mth.abs(j)<2))) worldgenlevel.setBlock(offset, Blocks.END_STONE.defaultBlockState(), 3);
                        }
                    }
                }
            }

            populateCenter(worldgenlevel, randomsource, blockpos);

            return true;
        } else {
            return false;
        }
    }

    private static void populateCenter(WorldGenLevel worldgenlevel, RandomSource randomsource, BlockPos blockpos) {
        switch (randomsource.nextInt(4)){
            case 0 : {
                BaubleEntity bauble = new BaubleEntity(BeyondEntityTypes.BAUBLE.get(), worldgenlevel.getLevel());
                bauble.setPos(blockpos.above().getCenter());
                worldgenlevel.getLevel().addFreshEntity(bauble);
                return;
            }
            case 1 : {
                worldgenlevel.setBlock(blockpos.below(), BeyondBlocks.GELLID_VOID.get().defaultBlockState(), 3);
                return;
            }
            case 3 : {
                worldgenlevel.setBlock(blockpos.below(), BeyondBlocks.PEARL_MIRROR.get().defaultBlockState().setValue(MirrorBlock.UP, true), 3);
                return;
            }
        }
    }

    private BlockState getBlockState(int x, int y, int i) {
        if (Mth.abs(x)==2 && Mth.abs(y)==2)
            return Blocks.END_STONE.defaultBlockState();
        return BeyondBlocks.PALE_RAKED_NACRE.get().defaultBlockState().setValue(RakedNacreBlock.RAKE_DIRECTION, RakedProperty.getRandom(i));
    }
}
