package com.thebeyond.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Placeholder for the network's film, see-through like stained glass. */
public class OrganicFilmBlock extends TransparentBlock {

    public OrganicFilmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OrganicTissueBlock.BAND, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(OrganicTissueBlock.BAND);
    }
}
