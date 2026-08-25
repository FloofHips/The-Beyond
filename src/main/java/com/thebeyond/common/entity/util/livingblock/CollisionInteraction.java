package com.thebeyond.common.entity.util.livingblock;

public enum CollisionInteraction {
    BLOCK,
    ENTITY,
    NONE;

    public boolean canBeCollidedWith() {
        return this == BLOCK;
    }

    public boolean canBeNudged() {
        return this == ENTITY;
    }

    public boolean isPushable() {
        return this != NONE;
    }
}
