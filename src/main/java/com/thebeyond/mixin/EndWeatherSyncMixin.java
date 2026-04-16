package com.thebeyond.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Syncs weather levels in the End with the overworld's weather state.
 *
 * Vanilla's advanceWeatherCycle() only updates rainLevel/thunderLevel for
 * dimensions with hasSkyLight=true (overworld). The End's levels stay stale
 * from world creation, breaking isRaining() and weather-dependent gameplay.
 *
 * This mixin runs after advanceWeatherCycle() and updates the End's
 * rain/thunder levels based on the shared levelData (DerivedLevelData
 * delegates to the overworld's weather state).
 */
@Mixin(ServerLevel.class)
public class EndWeatherSyncMixin {

    @Inject(method = "advanceWeatherCycle", at = @At("TAIL"))
    private void the_beyond$syncEndWeather(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.dimension() != Level.END) return;

        // Update rain level from shared levelData (DerivedLevelData -> overworld)
        self.oRainLevel = self.rainLevel;
        if (self.getLevelData().isRaining()) {
            self.rainLevel = Math.min(1.0F, self.rainLevel + 0.01F);
        } else {
            self.rainLevel = Math.max(0.0F, self.rainLevel - 0.01F);
        }
        self.rainLevel = Mth.clamp(self.rainLevel, 0.0F, 1.0F);

        // Update thunder level
        self.oThunderLevel = self.thunderLevel;
        if (self.getLevelData().isThundering()) {
            self.thunderLevel = Math.min(1.0F, self.thunderLevel + 0.01F);
        } else {
            self.thunderLevel = Math.max(0.0F, self.thunderLevel - 0.01F);
        }
        self.thunderLevel = Mth.clamp(self.thunderLevel, 0.0F, 1.0F);
    }
}
