package com.thebeyond.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zero/restore {@code eyeHeight}+{@code eyeHeightOld} around a manual render: {@code setup} adds the lerped eye height
 * to Y, but {@code tick()} (its only updater) doesn't run here, so both must be zeroed to land at the requested eye.
 */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("eyeHeight")
    float the_beyond$getEyeHeight();

    @Accessor("eyeHeight")
    void the_beyond$setEyeHeight(float eyeHeight);

    @Accessor("eyeHeightOld")
    float the_beyond$getEyeHeightOld();

    @Accessor("eyeHeightOld")
    void the_beyond$setEyeHeightOld(float eyeHeightOld);
}
