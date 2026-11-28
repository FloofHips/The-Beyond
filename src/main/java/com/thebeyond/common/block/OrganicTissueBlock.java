package com.thebeyond.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Placeholder for the organic network's tubes and roots, the band being the network distance. */
public class OrganicTissueBlock extends Block {
    public static final IntegerProperty BAND = IntegerProperty.create("band", 0, 8);

    public OrganicTissueBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BAND, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(BAND);
    }
}
