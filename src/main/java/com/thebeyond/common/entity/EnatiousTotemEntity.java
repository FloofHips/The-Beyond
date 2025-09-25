package com.thebeyond.common.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

public class EnatiousTotemEntity extends Mob implements Enemy {
    protected EnatiousTotemEntity(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }
}
