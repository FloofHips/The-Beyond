package com.thebeyond.mixin.client;

import com.thebeyond.client.event.specialeffects.EndSpecialEffects;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces Beyond's {@link EndSpecialEffects} for ANY End-related dimension type,
 * regardless of which mod registered the {@code DimensionSpecialEffects} for that key.
 *
 * <p>Without this mixin, BetterEnd (via BCLib/WorldWeaver) registers its own
 * {@code BetterEndSkyEffect} for {@code minecraft:the_end}, overriding Beyond's
 * custom sky, fog color, and lightmap adjustments. The NeoForge event-based
 * registration ({@code RegisterDimensionSpecialEffectsEvent}) cannot reliably
 * guarantee ordering across mods that use different registration mechanisms
 * (event bus, direct map access, etc.).</p>
 *
 * <p>This mixin intercepts {@code DimensionSpecialEffects.forType()} at HEAD and
 * returns Beyond's {@code EndSpecialEffects} for any dimension type whose
 * {@code effectsLocation} path is {@code "the_end"} — covering both
 * {@code the_beyond:the_end} and {@code minecraft:the_end}.</p>
 */
@Mixin(DimensionSpecialEffects.class)
public class EndDimensionEffectsMixin {

    @Unique
    private static EndSpecialEffects theBeyond$endEffects;

    @Inject(method = "forType", at = @At("HEAD"), cancellable = true)
    private static void theBeyond$overrideEndEffects(DimensionType type, CallbackInfoReturnable<DimensionSpecialEffects> cir) {
        ResourceLocation loc = type.effectsLocation();
        if (loc.getPath().equals("the_end")) {
            if (theBeyond$endEffects == null) {
                theBeyond$endEffects = new EndSpecialEffects();
            }
            cir.setReturnValue(theBeyond$endEffects);
        }
    }
}
