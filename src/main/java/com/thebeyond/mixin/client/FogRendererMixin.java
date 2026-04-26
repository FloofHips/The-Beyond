package com.thebeyond.mixin.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.BeyondConfig;
import com.thebeyond.client.event.ModClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels {@code FogRenderer.setupFog()} entirely for the End dimension,
 * replacing it with Beyond's fog distances. By canceling at HEAD, no other
 * fog processing runs — no vanilla computation, no NeoForge event dispatch,
 * no other mods' fog mixins or event handlers. The competition is disabled,
 * not outprioritized.
 *
 * <p>Gated by the clientside config option {@code enableCustomFog}. When the
 * config is disabled, vanilla {@code setupFog} runs normally for the End.</p>
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private static void theBeyond$forceEndFog(Camera camera, FogRenderer.FogMode fogMode,
                                               float farPlaneDistance, boolean shouldCreateFog,
                                               float partialTick, CallbackInfo ci) {
        if (camera.getEntity() != null
                && camera.getEntity().level().dimension() == Level.END
                && BeyondConfig.ENABLE_CUSTOM_FOG.get()) {
            float finalFog = (float) Mth.clamp(ModClientEvents.effectFog, 0.05, 1);
            float y = (float) camera.getEntity().position().y;
            // Clamp minimum fog end to 30 blocks so fog stays valid at negative Y
            // (Enderscape extends End min height to y=-64)
            float fogEnd = Math.max((y + 30) * finalFog, 30 * finalFog);
            RenderSystem.setShaderFogStart(15 * finalFog);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            ci.cancel();
        }
    }
}
