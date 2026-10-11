package com.thebeyond.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.client.renderer.blockentities.ProjectorDeferredDecal;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Fabulous composites its layers in color only, so the decal snapshots their depth before the composite. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererFabulousDepthMixin {
    @Shadow
    private PostChain transparencyChain;

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/PostChain;process(F)V"))
    private void the_beyond$snapshotFabulousDepth(PostChain chain, float partialTick, Operation<Void> original) {
        if (chain == transparencyChain) {
            ProjectorDeferredDecal.snapshotFabulousDepth((LevelRenderer) (Object) this);
        }
        original.call(chain, partialTick);
    }
}
