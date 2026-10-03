package com.thebeyond.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Swaps the main render target for a capture FBO, Sodium and Iris composite into whatever the field holds. */
@Mixin(Minecraft.class)
public interface MinecraftMainTargetAccessor {
    @Mutable
    @Accessor("mainRenderTarget")
    void the_beyond$setMainRenderTarget(RenderTarget target);
}
