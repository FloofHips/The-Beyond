package com.thebeyond.mixin.compat.jade;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "snownee.jade.overlay.RayTracing", remap = false)
public abstract class JadeRayTracingMixin {

    private static final double the_beyond$MAX_REASONABLE_AABB_SIZE = 1024.0;

    @Inject(method = "getEntityHitResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void the_beyond$bailOnGiantAABB(
            Level worldIn,
            Entity projectile,
            Vec3 startVec,
            Vec3 endVec,
            AABB boundingBox,
            Predicate<Entity> filter,
            CallbackInfoReturnable<EntityHitResult> cir) {
        if (boundingBox.getSize() > the_beyond$MAX_REASONABLE_AABB_SIZE) {
            cir.setReturnValue(null);
        }
    }
}
