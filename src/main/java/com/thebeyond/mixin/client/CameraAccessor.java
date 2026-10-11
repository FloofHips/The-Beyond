package com.thebeyond.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** eyeHeight and eyeHeightOld are zeroed around a manual render, since setup adds them and tick() does not run. */
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
