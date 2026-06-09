package com.thebeyond.util;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public interface ITeleportingEntity {
    default SoundEvent getTeleportingSound() {
        return SoundEvents.CHORUS_FRUIT_TELEPORT;
    }
}
