package com.thebeyond.common.block;

import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.registry.BeyondConfigFeatures;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.checkerframework.checker.units.qual.A;

import java.util.Optional;

public class ObirootSproutBlock extends Block implements BonemealableBlock, Fallable {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{Block.box(6.0, 8.0, 6.0, 10.0, 16.0, 10.0), Block.box(6.0, 0.0, 6.0, 10.0, 6.0, 10.0), Block.box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0), Block.box(4.0, 0.0, 4.0, 12.0, 8.0, 12.0)};

    public ObirootSproutBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(AGE, 0));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) == 0) return;

        float chance = level.isRaining() ? 0.5f : 0.1f;

        if (random.nextFloat() < chance) {
            if (state.getValue(AGE) == 3) {
                EnadrakeEntity enadrake = new EnadrakeEntity(BeyondEntityTypes.ENADRAKE.get(), level);

                if(level.addFreshEntity(enadrake)){
                    enadrake.setPos(pos.getX() + 0.5, pos.getY()+ 0.5, pos.getZ() + 0.5);
                    enadrake.setDeltaMovement(0, 0.3, 0);
                }
                level.playSound(null, pos, SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                return;
            }

        level.setBlockAndUpdate(pos, state.setValue(AGE, state.getValue(AGE) + 1));
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos) && state.getValue(AGE) == 0) level.scheduleTick(pos, this, 2);

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FallingBlockEntity fallingblockentity = FallingBlockEntity.fall(level, pos, state);
        fallingblockentity.disableDrop();
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        BlockState fallingState = fallingBlock.getBlockState();

        if (level.getBlockState(pos.below()).is(BeyondTags.END_FLOOR_BLOCKS)) {
            level.setBlockAndUpdate(pos, fallingState.setValue(AGE, 1));
        } else {
            if (!level.isClientSide) {
                ItemStack itemStack = new ItemStack(this.asItem());
                fallingBlock.spawnAtLocation(itemStack);
            }
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
        return blockState.getValue(AGE) > 0;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
        if (blockState.getValue(AGE) > 2) {
            growObiroot(serverLevel, blockPos, blockState, randomSource);
            return;
        }

        serverLevel.setBlockAndUpdate(blockPos, blockState.setValue(AGE, blockState.getValue(AGE) + 1));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(AGE) == 0) return !level.getBlockState(pos.above()).isAir();
        else return !level.getBlockState(pos.below()).isAir();
    }

    public static void growObiroot(ServerLevel level, BlockPos pos, BlockState blockState, RandomSource random) {
        TreeGrower obiroot = new TreeGrower("obiroot", Optional.empty(), Optional.of(BeyondConfigFeatures.OBIROOT), Optional.empty());
        obiroot.growTree(level, level.getChunkSource().getGenerator(), pos, blockState, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_AGE[(Integer)state.getValue(AGE)];
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{AGE});
    }
}
