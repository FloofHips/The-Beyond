package com.thebeyond.client.renderer;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class BeadRenderer extends LivingBlockRenderer {

    private final BlockState blockState;

    public BeadRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.blockState = BeyondBlocks.PEARL.get().defaultBlockState();
    }

    @Override
    public BlockState getBlockState() {
        return this.blockState;
    }
}
