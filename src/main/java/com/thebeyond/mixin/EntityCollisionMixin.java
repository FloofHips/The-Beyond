package com.thebeyond.mixin;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionHandler;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @Inject(
        method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void beyond$resolvePreciseCollision(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof LivingBlock mover) || !mover.orientedHull()) {
            return;
        }

        Vec3 resolved = LivingBlockCollisionHandler.resolveMovement(mover, movement);
        if (resolved != null) {
            cir.setReturnValue(resolved);
        }
    }

    @Inject(
        method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void beyond$slideAlongLivingFace(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Vec3 slide = LivingBlockCollisionHandler.slideOnBody(
                (Entity) (Object) this, movement, cir.getReturnValue());
        if (slide != null) {
            cir.setReturnValue(slide);
        }
    }
}
