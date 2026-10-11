package com.thebeyond.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes Sodium's live manager so the block camera can swap a spare render context onto it. */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public interface SodiumWorldRendererAccessor {
    @Accessor("renderSectionManager")
    RenderSectionManager the_beyond$renderSectionManager();
}
