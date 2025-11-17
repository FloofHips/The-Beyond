package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EctoplasmBlock extends HalfTransparentBlock {
    public static final IntegerProperty AGE;
    public EctoplasmBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(AGE, 0));
    }
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, Mth.nextInt(level.getRandom(), 120, 220));
    }
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, Mth.nextInt(random, 60, 120));
        this.slightlyDie(state, level, pos);
    }
    private boolean slightlyDie(BlockState state, Level level, BlockPos pos) {
        int i = state.getValue(AGE);
        if (i < 2) {
            level.playSound(null, pos, SoundEvents.BREEZE_INHALE, SoundSource.BLOCKS,1,1);
            level.setBlock(pos, state.setValue(AGE, i + 1), 2);
            return false;
        } else {
            level.playSound(null, pos, SoundEvents.BREEZE_DEATH, SoundSource.BLOCKS,1,1);
            this.die(state, level, pos);
            return true;
        }
    }

    protected void die(BlockState state, Level level, BlockPos pos) {
        level.removeBlock(pos, false);
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{AGE});
    }

    static {
        AGE = BlockStateProperties.AGE_2;
    }
}
