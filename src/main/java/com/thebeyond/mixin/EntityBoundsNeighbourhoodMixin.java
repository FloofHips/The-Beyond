package com.thebeyond.mixin;

import com.thebeyond.common.entity.util.livingblock.LivingBlockNeighbourhood;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityBoundsNeighbourhoodMixin {
    @Inject(method = "setBoundingBox", at = @At("HEAD"))
    private void the_beyond$staleNeighbourhood(AABB bb, CallbackInfo ci) {
        LivingBlockNeighbourhood.onBoundsChange((Entity) (Object) this, bb);
    }
}
