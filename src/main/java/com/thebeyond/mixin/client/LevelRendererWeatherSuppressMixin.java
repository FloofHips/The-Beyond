package com.thebeyond.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No rain or snow strands in the End, modded biomes with precipitation would leak them. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererWeatherSuppressMixin {
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void the_beyond$skipEndWeather(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension() == Level.END) {
            ci.cancel();
        }
    }
}
