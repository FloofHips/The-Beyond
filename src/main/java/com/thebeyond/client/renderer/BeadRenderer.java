package com.thebeyond.client.renderer;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BeadRenderer extends LivingBlockRenderer {

    private static final BlockState BLOCK_STATE = BeyondBlocks.PEARL.get().defaultBlockState();

    public BeadRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public BlockState getBlockState() {
        return BLOCK_STATE;
    }
}
