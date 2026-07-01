package com.thebeyond.mixin.compat.sodium;

import com.thebeyond.compat.sodium.client.ISodiumSectionManagerSwap;
import com.thebeyond.compat.sodium.client.SodiumRenderContext;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Swaps in a spare {@link SodiumRenderContext} so the block camera renders its own POV, then swaps back. {@code remap = false}: Sodium classes keep their own names. */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class SodiumRenderSectionManagerMixin implements ISodiumSectionManagerSwap {
    @Shadow
    @Final
    @Mutable
    private int renderDistance;

    @Shadow
    private SortedRenderLists renderLists;

    @Override
    public void the_beyond$swapContext(SodiumRenderContext context) {
        SortedRenderLists tmpLists = this.renderLists;
        this.renderLists = context.renderLists;
        context.renderLists = tmpLists;

        int tmpDistance = this.renderDistance;
        this.renderDistance = context.renderDistance;
        context.renderDistance = tmpDistance;
    }
}
