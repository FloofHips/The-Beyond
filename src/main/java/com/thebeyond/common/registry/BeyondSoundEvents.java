package com.thebeyond.common.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> END_STONE_BREAK = SOUND_EVENTS.register("block.end_stone.break", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> END_STONE_STEP = SOUND_EVENTS.register("block.end_stone.step", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> END_STONE_PLACE = SOUND_EVENTS.register("block.end_stone.place", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> END_STONE_HIT = SOUND_EVENTS.register("block.end_stone.hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> END_STONE_FALL = SOUND_EVENTS.register("block.end_stone.fall", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> PLATED_END_STONE_BREAK = SOUND_EVENTS.register("block.plated_end_stone.break", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATED_END_STONE_STEP = SOUND_EVENTS.register("block.plated_end_stone.step", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATED_END_STONE_PLACE = SOUND_EVENTS.register("block.plated_end_stone.place", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATED_END_STONE_HIT = SOUND_EVENTS.register("block.plated_end_stone.hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATED_END_STONE_FALL = SOUND_EVENTS.register("block.plated_end_stone.fall", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_BLOCK_BREAK = SOUND_EVENTS.register("block.plate_block.break", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_BLOCK_STEP = SOUND_EVENTS.register("block.plate_block.step", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_BLOCK_PLACE = SOUND_EVENTS.register("block.plate_block.place", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_BLOCK_HIT = SOUND_EVENTS.register("block.plate_block.hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_BLOCK_FALL = SOUND_EVENTS.register("block.plate_block.fall", SoundEvent::createVariableRangeEvent);
}
