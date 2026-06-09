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

/** Forces Beyond's {@link EndSpecialEffects} for any dim type whose {@code effectsLocation}
 *  path is {@code "the_end"}. BetterEnd/BCLib otherwise wins the registration race for
 *  {@code minecraft:the_end} via {@code RegisterDimensionSpecialEffectsEvent}. */
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
