package com.thebeyond.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.BeyondConfig;
import com.thebeyond.client.compat.NightVisionFogGuard;
import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.client.renderer.RenderFailureLog;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.multiplayer.ClientLevel;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels setupFog in the End and writes Beyond's distances ahead of every other fog logic, gated by enableCustomFog. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

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

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F", ordinal = 1))
    private static float theBeyond$nightVisionKeepsSign(float a, float b, Operation<Float> original) {
        // vanilla passes 1/fogRed here, anything else means the ordinal now points at another Math.min
        if (Float.floatToRawIntBits(a) != Float.floatToRawIntBits(1.0F / fogRed)) {
            RenderFailureLog.error("[TheBeyond] night vision fog fix skipped an unexpected Math.min in setupColor", new IllegalStateException("a=" + a));
            return original.call(a, b);
        }
        if (fogRed < 0 || fogGreen < 0 || fogBlue < 0) {
            float big = Math.max(Math.abs(fogRed), Math.max(Math.abs(fogGreen), Math.abs(fogBlue)));
            return big >= Float.MIN_NORMAL ? 1.0F / big : 1.0F;
        }
        return original.call(a, b);
    }

    // require 0 so an overwrite of setupColor that drops this NeoForge hook only loses the guard's check, not the game
    @Inject(method = "setupColor", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;getFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFFFF)Lorg/joml/Vector3f;"))
    private static void theBeyond$afterNightVision(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float boss, CallbackInfo ci) {
        NightVisionFogGuard.afterNightVision(fogRed, fogGreen, fogBlue);
    }

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void theBeyond$setupColorEnded(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float boss, CallbackInfo ci) {
        NightVisionFogGuard.setupColorEnded(fogRed, fogGreen, fogBlue);
    }
}
