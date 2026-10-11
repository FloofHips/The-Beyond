package com.thebeyond.mixin.compat.simulated;

import com.thebeyond.BeyondConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lowers the void-death line with a sea below the floor (negative VOID_SEA_OFFSET), never above vanilla. */
@Mixin(Entity.class)
public abstract class EndVoidFloorMixin {

    // a redirect allocates no CallbackInfo per entity tick, same target as SimContraptionFloorMixin
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
