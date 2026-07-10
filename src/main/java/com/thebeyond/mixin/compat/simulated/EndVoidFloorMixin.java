package com.thebeyond.mixin.compat.simulated;

import com.thebeyond.BeyondConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lowers the End's void-death line in step with a below-floor void sea (negative {@link BeyondConfig#VOID_SEA_OFFSET})
 *  so a rider isn't voided out; a non-negative offset is a no-op, so the line never rises above vanilla. */
@Mixin(Entity.class)
public abstract class EndVoidFloorMixin {

    // Redirect (not a cancellable @Inject) so this per-entity-per-tick path allocates no CallbackInfo. checkBelowWorld voids
    // out below getMinBuildHeight()-64; same target as SimContraptionFloorMixin, so the pilot and contraption floors move in lockstep.
    @Redirect(
            method = "checkBelowWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"),
            require = 1)
    private int the_beyond$lowerEndVoidFloor(Level level) {
        int minY = level.getMinBuildHeight();
        int offset = BeyondConfig.VOID_SEA_OFFSET.get();
        if (offset >= 0 || level.dimension() != Level.END) return minY;
        return minY + offset;
    }
}
