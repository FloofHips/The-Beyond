package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockNeighbourhood;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPushNeighbourhoodMixin {
    @WrapOperation(method = "pushEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Entity> the_beyond$pushFromNeighbourhood(Level level, Entity except, AABB area, Predicate<? super Entity> predicate,
                                                         Operation<List<Entity>> original) {
        if ((Object) this instanceof LivingBlock) {
            return LivingBlockNeighbourhood.getEntities(level, except, area, predicate);
        }
        return original.call(level, except, area, predicate);
    }
}
