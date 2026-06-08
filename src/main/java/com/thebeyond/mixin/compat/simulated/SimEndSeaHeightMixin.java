package com.thebeyond.mixin.compat.simulated;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.end_sea.EndSeaPhysicsData", remap = false)
public abstract class SimEndSeaHeightMixin {

    @Unique private static final int the_beyond$VOID_SEA_OFFSET_ABOVE_FLOOR = 2;

    @Unique private static boolean the_beyond$reflectFailed = false;
    @Unique private static Method the_beyond$mDimension;
    @Unique private static Method the_beyond$mPriority;
    @Unique private static Method the_beyond$mStartY;
    @Unique private static Method the_beyond$mDepthGradient;
    @Unique private static Method the_beyond$mDrag;
    @Unique private static Constructor<?> the_beyond$ctor;

    @Inject(method = "of", at = @At("RETURN"), cancellable = true, remap = false)
    private static void the_beyond$adjustVoidSeaHeight(Level level, CallbackInfoReturnable<Object> cir) {
        if (the_beyond$reflectFailed) return;

        Object original = cir.getReturnValue();
        if (original == null) return;
        if (!Level.END.equals(level.dimension())) return;

        try {
            if (the_beyond$ctor == null) the_beyond$resolve(original.getClass());

            double targetStartY = level.getMinBuildHeight() + the_beyond$VOID_SEA_OFFSET_ABOVE_FLOOR;
            double currentStartY = (double) the_beyond$mStartY.invoke(original);
            if (Math.abs(currentStartY - targetStartY) < 1.0e-9) return;

            Object dimension = the_beyond$mDimension.invoke(original);
            Object priority = the_beyond$mPriority.invoke(original);
            double depthGradient = (double) the_beyond$mDepthGradient.invoke(original);
            double drag = (double) the_beyond$mDrag.invoke(original);

            cir.setReturnValue(the_beyond$ctor.newInstance(
                    dimension, priority, targetStartY, depthGradient, drag));
        } catch (Throwable t) {
            the_beyond$reflectFailed = true;
        }
    }

    @Unique
    private static void the_beyond$resolve(Class<?> endSeaPhysics) throws NoSuchMethodException {
        the_beyond$mDimension = endSeaPhysics.getMethod("dimension");
        the_beyond$mPriority = endSeaPhysics.getMethod("priority");
        the_beyond$mStartY = endSeaPhysics.getMethod("startY");
        the_beyond$mDepthGradient = endSeaPhysics.getMethod("depthGradient");
        the_beyond$mDrag = endSeaPhysics.getMethod("drag");
        the_beyond$ctor = endSeaPhysics.getDeclaredConstructor(
                ResourceLocation.class, Optional.class, double.class, double.class, double.class);
    }
}
