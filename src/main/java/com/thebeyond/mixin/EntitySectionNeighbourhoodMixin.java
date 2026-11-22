package com.thebeyond.mixin;

import com.thebeyond.common.entity.util.livingblock.LivingBlockNeighbourhood;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySection.class)
public abstract class EntitySectionNeighbourhoodMixin {
    @Inject(method = "add", at = @At("HEAD"))
    private void the_beyond$joined(EntityAccess entity, CallbackInfo ci) {
        LivingBlockNeighbourhood.onSectionJoin(entity);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void the_beyond$left(EntityAccess entity, CallbackInfoReturnable<Boolean> cir) {
        LivingBlockNeighbourhood.onSectionLeave(entity);
    }

    @Inject(method = "updateChunkStatus", at = @At("HEAD"))
    private void the_beyond$visibility(Visibility visibility, CallbackInfoReturnable<Visibility> cir) {
        LivingBlockNeighbourhood.onVisibilityChange();
    }
}
