package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.block.EnadrakeHutBlock;
import com.thebeyond.common.block.blockentities.EnadrakeHutBlockEntity;
import com.thebeyond.common.block.blockstates.HutHeightProperty;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

public class EnadrakeVillageFeature extends Feature<NoneFeatureConfiguration> {
    public EnadrakeVillageFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> featurePlaceContext) {
        WorldGenLevel level = featurePlaceContext.level();
        BlockPos origin = featurePlaceContext.origin();

        int radius = level.getRandom().nextInt( 2,6);
        int houseCount = level.getRandom().nextInt(radius, radius*2);

        setBlock(level, origin.below(), BeyondBlocks.ENATIOUS_TOTEM_SEED.get().defaultBlockState());

        for (int i = 0; i < houseCount; i++) {
            double angle = (2 * Math.PI * i) / houseCount;

            int xOffset = (int) Math.round(Math.cos(angle) * radius);
            int zOffset = (int) Math.round(Math.sin(angle) * radius);

            BlockPos housePos = origin.offset(xOffset, 0, zOffset);

            BlockPos groundPos = findGroundPos(level, housePos);
            int height = level.getRandom().nextInt(1, 5);

            BlockState[] states = new BlockState[height];

            if (height == 1) {
                states[0] = BeyondBlocks.ENADRAKE_HUT.get().defaultBlockState().setValue(EnadrakeHutBlock.HEIGHT, HutHeightProperty.TIP).setValue(EnadrakeHutBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()));
            }

            if (height == 2) {
                states[0] = BeyondBlocks.ENADRAKE_HUT.get().defaultBlockState().setValue(EnadrakeHutBlock.HEIGHT, HutHeightProperty.BASE).setValue(EnadrakeHutBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()));
                states[1] = BeyondBlocks.ENADRAKE_HUT.get().defaultBlockState().setValue(EnadrakeHutBlock.HEIGHT, HutHeightProperty.TOP).setValue(EnadrakeHutBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()));
            }

            if (height > 2) {
                states[0] = BeyondBlocks.ENADRAKE_HUT.get().defaultBlockState().setValue(EnadrakeHutBlock.HEIGHT, HutHeightProperty.BASE).setValue(EnadrakeHutBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()));
                for (int j = 0; j < height; j++) {
                    states[j] = BeyondBlocks.ENADRAKE_HUT.get().defaultBlockState().setValue(EnadrakeHutBlock.HEIGHT, HutHeightProperty.CORE).setValue(EnadrakeHutBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()));
                }
                states[height-1] = BeyondBlocks.ENADRAKE_HUT.get().defaultBlockState().setValue(EnadrakeHutBlock.HEIGHT, HutHeightProperty.TOP).setValue(EnadrakeHutBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()));
            }

            if (groundPos != null) {
                for (int j = 0; j < height; j++) {
                    BlockPos pos = groundPos.offset(0,j,0);
                    if (level.isEmptyBlock(pos)) {

                        level.setBlock(pos, states[j], Block.UPDATE_ALL);

                        if (level.getBlockEntity(pos) instanceof EnadrakeHutBlockEntity be) {

                            //EnadrakeEntity entity = BeyondEntityTypes.ENADRAKE.get().create(level.getLevel());
                            //entity.finalizeSpawn(level.getLevel(), level.getLevel().getCurrentDifficultyAt(groundPos), MobSpawnType.NATURAL, null);

                            //level.getLevel().addFreshEntity(entity);

                            //be.tryToEnter(entity);
                        }
                    } else break;
                }
            }
        }

        return true;
    }

    private BlockPos findGroundPos(WorldGenLevel level, BlockPos pos) {
        for (int y = pos.getY() + 5; y > pos.getY() - 5; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (!level.isEmptyBlock(checkPos) && level.getBlockState(checkPos).is(BeyondTags.END_FLOOR_BLOCKS)) {
                return checkPos.above();
            }
        }
        return null;
    }
}
