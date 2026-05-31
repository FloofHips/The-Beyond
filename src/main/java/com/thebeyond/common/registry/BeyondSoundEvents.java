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

    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_CRYSTAL_BREAK = SOUND_EVENTS.register("block.void_crystal.break", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_CRYSTAL_STEP = SOUND_EVENTS.register("block.void_crystal.step", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_CRYSTAL_PLACE = SOUND_EVENTS.register("block.void_crystal.place", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_CRYSTAL_HIT = SOUND_EVENTS.register("block.void_crystal.hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_CRYSTAL_FALL = SOUND_EVENTS.register("block.void_crystal.fall", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_CRYSTAL_SHATTER = SOUND_EVENTS.register("block.void_crystal.shatter", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_DEATH = SOUND_EVENTS.register("entity.enderglop.death", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_DEATH_SMALL = SOUND_EVENTS.register("entity.enderglop.death_small", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_HURT = SOUND_EVENTS.register("entity.enderglop.hurt", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_HURT_SMALL = SOUND_EVENTS.register("entity.enderglop.hurt_small", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_JUMP = SOUND_EVENTS.register("entity.enderglop.jump", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_SQUISH = SOUND_EVENTS.register("entity.enderglop.squish", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_SQUISH_SMALL = SOUND_EVENTS.register("entity.enderglop.squish_small", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_VIBRATE = SOUND_EVENTS.register("entity.enderglop.vibrate", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_ARMOR = SOUND_EVENTS.register("entity.enderglop.armor", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_ARMOR_HURT = SOUND_EVENTS.register("entity.enderglop.armor_hurt", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENDERGLOP_ARMOR_BREAK = SOUND_EVENTS.register("entity.enderglop.armor_break", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> PULL = SOUND_EVENTS.register("item.magnet.pull", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGNET_FAIL = SOUND_EVENTS.register("item.magnet.fail", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGNET_SUCCESS = SOUND_EVENTS.register("item.magnet.success", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> POLAR_CHARGE = SOUND_EVENTS.register("block.polar.charge", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> POLAR_COOL = SOUND_EVENTS.register("block.polar.cool", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> POLAR_EMERGE = SOUND_EVENTS.register("block.polar.emerge", SoundEvent::createVariableRangeEvent);

}
