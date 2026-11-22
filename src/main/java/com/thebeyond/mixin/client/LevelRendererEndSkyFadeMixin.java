package com.thebeyond.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.client.compat.ExecutiveZones;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererEndSkyFadeMixin {

    @Unique private boolean the_beyond$fadedEndSky;

    @Inject(method = "renderEndSky", at = @At("HEAD"))
    private void the_beyond$fadeEndSkyIn(PoseStack poseStack, CallbackInfo ci) {
        float alpha = ExecutiveZones.takeEndSkyAlpha();
        if (alpha >= 1f) return;
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        this.the_beyond$fadedEndSky = true;
    }

    @Inject(method = "renderEndSky", at = @At("RETURN"))
    private void the_beyond$restoreSkyColor(PoseStack poseStack, CallbackInfo ci) {
        if (!this.the_beyond$fadedEndSky) return;
        this.the_beyond$fadedEndSky = false;
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
