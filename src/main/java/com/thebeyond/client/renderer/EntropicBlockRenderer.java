package com.thebeyond.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class EntropicBlockRenderer extends LivingBlockRenderer {

    private static final BlockState BLOCK_STATE = Blocks.LIME_CONCRETE.defaultBlockState();

    public EntropicBlockRenderer(final EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public BlockState getBlockState() {
        return BLOCK_STATE;
    }
}
