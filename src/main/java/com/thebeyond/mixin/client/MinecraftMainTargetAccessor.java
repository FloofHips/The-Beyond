package com.thebeyond.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Swaps Minecraft's main render target field for a capture FBO: Sodium/Iris composite into whatever
 * {@code getMainRenderTarget()} returns, so binding alone won't route the scene — the field must change.
 */
@Mixin(Minecraft.class)
public interface MinecraftMainTargetAccessor {
    @Mutable
    @Accessor("mainRenderTarget")
    void the_beyond$setMainRenderTarget(RenderTarget target);
}
