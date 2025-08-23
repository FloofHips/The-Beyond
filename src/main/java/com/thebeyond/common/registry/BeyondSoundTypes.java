package com.thebeyond.common.registry;

import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.common.util.DeferredSoundType;

public class BeyondSoundTypes {

    public static SoundType END_STONE = new DeferredSoundType(1.0F, 1.0F,
            BeyondSoundEvents.END_STONE_BREAK,
            BeyondSoundEvents.END_STONE_STEP,
            BeyondSoundEvents.END_STONE_PLACE,
            BeyondSoundEvents.END_STONE_HIT,
            BeyondSoundEvents.END_STONE_FALL
    );

    public static SoundType PLATED_END_STONE_BLOCK = new DeferredSoundType(1.0F, 1.0F,
            BeyondSoundEvents.PLATED_END_STONE_BREAK,
            BeyondSoundEvents.PLATED_END_STONE_STEP,
            BeyondSoundEvents.PLATED_END_STONE_PLACE,
            BeyondSoundEvents.PLATED_END_STONE_HIT,
            BeyondSoundEvents.PLATED_END_STONE_FALL
    );

    public static SoundType PLATE_BLOCK = new DeferredSoundType(1.0F, 1.0F,
            BeyondSoundEvents.PLATE_BLOCK_BREAK,
            BeyondSoundEvents.PLATE_BLOCK_STEP,
            BeyondSoundEvents.PLATE_BLOCK_PLACE,
            BeyondSoundEvents.PLATE_BLOCK_HIT,
            BeyondSoundEvents.PLATE_BLOCK_FALL
    );

    public static SoundType POLAR_ANTENNA = new DeferredSoundType(1.0F, 2F,
            BeyondSoundEvents.PLATE_BLOCK_BREAK,
            BeyondSoundEvents.PLATE_BLOCK_STEP,
            BeyondSoundEvents.PLATE_BLOCK_PLACE,
            BeyondSoundEvents.PLATE_BLOCK_HIT,
            BeyondSoundEvents.PLATE_BLOCK_FALL
    );

}
