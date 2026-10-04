package com.thebeyond.mixin.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.BeyondConfig;
import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels setupFog in the End and writes Beyond's distances ahead of every other fog logic, gated by enableCustomFog. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    private static float theBeyond$fogEndAddon = 1;
    private static float theBeyond$fogNearAddon = 1;

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private static void theBeyond$forceEndFog(Camera camera, FogRenderer.FogMode fogMode, float farPlaneDistance, boolean shouldCreateFog, float partialTick, CallbackInfo ci) {
        if (camera.getEntity() != null && camera.getEntity().level().dimension() == Level.END && BeyondConfig.ENABLE_CUSTOM_FOG.get()) {
            float fogEndMultiplier = 1;
            float fogNearMultiplier = 1;
            float finalFog = ModClientEvents.finalEffectFog(camera);

            int distance = Minecraft.getInstance().options.renderDistance().get()*16;

            Holder<Biome> biome = camera.getEntity().level().getBiome(camera.getEntity().getOnPos().above());

            if (biome.is(BeyondTags.IS_FOGGY)) {
                fogEndMultiplier = 0.5f;
                fogNearMultiplier = 0.1f;
            }
            else if (biome.is(BeyondTags.IS_EXTRA_FOGGY)) {
                fogEndMultiplier = 0.2f;
                fogNearMultiplier = 0f;
            }
            else {
                fogEndMultiplier = 1;
                fogNearMultiplier = 1;
            }

            theBeyond$fogEndAddon = Mth.lerp(0.01f, theBeyond$fogEndAddon, fogEndMultiplier);
            theBeyond$fogNearAddon = Mth.lerp(0.05f, theBeyond$fogNearAddon, fogNearMultiplier);
            float fogEnd = distance * finalFog * theBeyond$fogEndAddon;

            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogStart(Mth.lerp(ModClientEvents.bossFog,15 * finalFog * theBeyond$fogNearAddon,0));
            RenderSystem.setShaderFogEnd(fogEnd);


            FluidState state = camera.getEntity().level().getFluidState(camera.getBlockPosition());
            if (camera.getPosition().y < (double) ((float) camera.getBlockPosition().getY() + state.getHeight(camera.getEntity().level(), camera.getBlockPosition())))
                IClientFluidTypeExtensions.of(state).modifyFogRender(camera, fogMode, distance/16, partialTick, 0, 10, FogShape.SPHERE);

            ci.cancel();
        }
    }
}
