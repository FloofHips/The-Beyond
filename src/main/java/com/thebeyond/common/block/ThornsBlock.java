package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.TriState;

public class ThornsBlock extends BranchBlock {
    private static final Direction[] DIRECTIONS = Direction.values();

    public ThornsBlock(Properties p_51707_) {
        super(p_51707_, 0.125f);
        this.registerDefaultState(this.stateDefinition.any().setValue(NORTH, Boolean.valueOf(false)).setValue(EAST, Boolean.valueOf(false)).setValue(SOUTH, Boolean.valueOf(false)).setValue(WEST, Boolean.valueOf(false)).setValue(UP, Boolean.valueOf(false)).setValue(DOWN, Boolean.valueOf(false)));
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapeByIndex[this.getAABBIndex(state)];
    }

    public static BlockState getStateWithConnections(BlockGetter level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        boolean down = level.getBlockState(pos.below()).is(block) || level.getBlockState(pos.below()).isSolid();
        boolean up = level.getBlockState(pos.above()).is(block) || level.getBlockState(pos.above()).isSolid();
        boolean north = level.getBlockState(pos.north()).is(block) || level.getBlockState(pos.north()).isSolid();
        boolean east = level.getBlockState(pos.east()).is(block) || level.getBlockState(pos.east()).isSolid();
        boolean south = level.getBlockState(pos.south()).is(block) || level.getBlockState(pos.south()).isSolid();
        boolean west = level.getBlockState(pos.west()).is(block) || level.getBlockState(pos.west()).isSolid();

        return state
                .trySetValue(DOWN, down)
                .trySetValue(UP, up)
                .trySetValue(NORTH, north)
                .trySetValue(EAST, east)
                .trySetValue(SOUTH, south)
                .trySetValue(WEST, west);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (Direction d : Direction.values()) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(d))) {
                BlockPos offset = pos.offset(d.getStepX(), d.getStepY(), d.getStepZ());
                if (level.random.nextFloat() > 0.05f && level.getBlockState(offset).getBlock() instanceof ThornsBlock) {
                    level.scheduleTick(offset, level.getBlockState(offset).getBlock(), 1);
                }
            }
        }
        level.destroyBlock(pos, true);
        super.tick(state, level, pos, random);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        for (Direction d : Direction.values()) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(d))) {
                BlockPos offset = pos.offset(d.getStepX(), d.getStepY(), d.getStepZ());
                if (level.getBlockState(offset).getBlock() instanceof ThornsBlock)
                    level.scheduleTick(offset, level.getBlockState(offset).getBlock(), 1);
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
}
