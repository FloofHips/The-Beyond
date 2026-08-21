package com.thebeyond.common.block;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static net.minecraft.core.Direction.Axis.Y;

public class CoilVertebraeBlock extends RotatedPillarBlock {

    protected static final VoxelShape Y_AXIS_AABB = Block.box((double)4.0F, (double)0.0F, (double)4.0F, (double)12.0F, (double)16.0F, (double)12.0F);
    protected static final VoxelShape Z_AXIS_AABB = Block.box((double)4.0F, (double)4.0F, (double)0.0F, (double)12.0F, (double)12.0F, (double)16.0F);
    protected static final VoxelShape X_AXIS_AABB = Block.box((double)0.0F, (double)4.0F, (double)4.0F, (double)16.0F, (double)12.0F, (double)12.0F);

    public CoilVertebraeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)this.defaultBlockState().setValue(AXIS, Y));
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return state.getValue(AXIS)==Y;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(AXIS)==Y) return Block.box((double)7.0F, (double)0.0F, (double)7.0F, (double)9.0F, (double)16.0F, (double)9.0F);
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            default -> X_AXIS_AABB;
            case Z -> Z_AXIS_AABB;
            case Y -> Y_AXIS_AABB;
        };
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        trySpreadBreak(state, level, pos);
        level.destroyBlock(pos, true);
        super.tick(state, level, pos, random);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        trySpreadBreak(state, level, pos);
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    private void trySpreadBreak(BlockState state, Level level, BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);
        switch (axis) {
            case X -> {
                scheduleTick(level, axis, pos.offset(1, 0, 0));
                scheduleTick(level, axis, pos.offset(-1, 0, 0));
                break;
            }
            case Y -> {
                scheduleTick(level, axis, pos.offset(0, 1, 0));
                scheduleTick(level, axis, pos.offset(0, -1, 0));
                break;
            }
            case Z -> {
                scheduleTick(level, axis, pos.offset(0, 0, 1));
                scheduleTick(level, axis, pos.offset(0, 0, -1));
                break;
            }
        }
    }

    public void scheduleTick(Level level, Direction.Axis axis, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof CoilVertebraeBlock) {
            if (level.getBlockState(pos).getValue(AXIS) == axis)
                level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 1);
        }
    }
}
