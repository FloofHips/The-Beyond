package com.thebeyond.client.renderer;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.renderer.util.LivingBlockSkin;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class BeadRenderer extends LivingBlockRenderer {

    public BeadRenderer(final EntityRendererProvider.Context context) {
        super(context, "pearl");
    }
}