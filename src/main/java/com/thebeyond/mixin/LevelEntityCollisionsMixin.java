package com.thebeyond.mixin;

import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionShapes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

import javax.annotation.Nullable;

@Mixin(Level.class)
public abstract class LevelEntityCollisionsMixin implements CommonLevelAccessor {

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB collisionBox) {
        return LivingBlockCollisionShapes.entityCollisions((Level) (Object) this, entity, collisionBox);
    }
}
