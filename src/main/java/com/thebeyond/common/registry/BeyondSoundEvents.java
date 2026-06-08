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

    public static final DeferredHolder<SoundEvent, SoundEvent> AURORACITE_BREAK = SOUND_EVENTS.register("block.auroracite.break", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> AURORACITE_STEP = SOUND_EVENTS.register("block.auroracite.step", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> AURORACITE_PLACE = SOUND_EVENTS.register("block.auroracite.place", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> AURORACITE_HIT = SOUND_EVENTS.register("block.auroracite.hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> AURORACITE_FALL = SOUND_EVENTS.register("block.auroracite.fall", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_BREAK = SOUND_EVENTS.register("block.memor.break", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_STEP = SOUND_EVENTS.register("block.memor.step", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_PLACE = SOUND_EVENTS.register("block.memor.place", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_HIT = SOUND_EVENTS.register("block.memor.hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FALL = SOUND_EVENTS.register("block.memor.fall", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_BREAK = SOUND_EVENTS.register("block.bonfire.break", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_STEP = SOUND_EVENTS.register("block.bonfire.step", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_PLACE = SOUND_EVENTS.register("block.bonfire.place", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_HIT = SOUND_EVENTS.register("block.bonfire.hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_FALL = SOUND_EVENTS.register("block.bonfire.fall", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> ECTOPLASM_BREAK = SOUND_EVENTS.register("block.ectoplasm.break", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ECTOPLASM_STEP = SOUND_EVENTS.register("block.ectoplasm.step", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ECTOPLASM_PLACE = SOUND_EVENTS.register("block.ectoplasm.place", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ECTOPLASM_HIT = SOUND_EVENTS.register("block.ectoplasm.hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ECTOPLASM_FALL = SOUND_EVENTS.register("block.ectoplasm.fall", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ECTOPLASM_WARN = SOUND_EVENTS.register("block.ectoplasm.warn", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ECTOPLASM_POP = SOUND_EVENTS.register("block.ectoplasm.pop", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_IDLE = SOUND_EVENTS.register("block.bonfire.idle", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_IDLE_CORRUPTED = SOUND_EVENTS.register("block.bonfire.corrupted_idle", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_IGNITE = SOUND_EVENTS.register("block.bonfire.ignite", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_SEARCH = SOUND_EVENTS.register("block.bonfire.search", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> BONFIRE_ACTIVATE = SOUND_EVENTS.register("block.bonfire.activate", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAME_FAIL = SOUND_EVENTS.register("item.flame.fail", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FAUCET_ABSORB = SOUND_EVENTS.register("block.memor_faucet.absorb", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FAUCET_OPEN = SOUND_EVENTS.register("block.memor_faucet.open", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FAUCET_CLOSE = SOUND_EVENTS.register("block.memor_faucet.close", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FAUCET_POWER1 = SOUND_EVENTS.register("block.memor_faucet.power1", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FAUCET_POWER2 = SOUND_EVENTS.register("block.memor_faucet.power2", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FAUCET_POWER3 = SOUND_EVENTS.register("block.memor_faucet.power3", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FAUCET_POWER4 = SOUND_EVENTS.register("block.memor_faucet.power4", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMOR_FAUCET_POWER_UP = SOUND_EVENTS.register("block.memor_faucet.power_final", SoundEvent::createVariableRangeEvent);

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

    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_DEATH = SOUND_EVENTS.register("entity.abyssal_nomad.death", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_HURT = SOUND_EVENTS.register("entity.abyssal_nomad.hurt", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_ATTACK = SOUND_EVENTS.register("entity.abyssal_nomad.attack", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_DANGER = SOUND_EVENTS.register("entity.abyssal_nomad.danger", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_HEAL = SOUND_EVENTS.register("entity.abyssal_nomad.heal", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_IDLE = SOUND_EVENTS.register("entity.abyssal_nomad.idle", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_NOD = SOUND_EVENTS.register("entity.abyssal_nomad.nod", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_THANK = SOUND_EVENTS.register("entity.abyssal_nomad.thank", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_TELEPORT = SOUND_EVENTS.register("entity.abyssal_nomad.teleport", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_TEAR = SOUND_EVENTS.register("entity.abyssal_nomad.tear", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_REMEMBER = SOUND_EVENTS.register("entity.abyssal_nomad.remember", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_NOMAD_DECRYPT = SOUND_EVENTS.register("entity.abyssal_nomad.decrypt", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> ENATIOUS_TOTEM_DEATH = SOUND_EVENTS.register("entity.enatious_totem.death", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENATIOUS_TOTEM_HURT = SOUND_EVENTS.register("entity.enatious_totem.hurt", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENATIOUS_TOTEM_TELEPORT = SOUND_EVENTS.register("entity.enatious_totem.teleport", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENATIOUS_TOTEM_LEAVE = SOUND_EVENTS.register("entity.enatious_totem.leave", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENATIOUS_TOTEM_READY = SOUND_EVENTS.register("entity.enatious_totem.ready", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ROOTS_CREAKING = SOUND_EVENTS.register("entity.enatious_totem.roots_creaking", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENATIOUS_TOTEM_SHOCKWAVE = SOUND_EVENTS.register("entity.enatious_totem.shockwave", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENATIOUS_TOTEM_SPAWN = SOUND_EVENTS.register("entity.enatious_totem.spawn", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENATIOUS_TOTEM_SHOOT = SOUND_EVENTS.register("entity.enatious_totem.shoot", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> SEED_KNOCKBACK_BURST = SOUND_EVENTS.register("entity.seed.knockback_burst", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEED_POISON_BOUNCE = SOUND_EVENTS.register("entity.seed.poison_bounce", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEED_POISON_LAND = SOUND_EVENTS.register("entity.seed.poison_land", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEED_UNSTABLE_BURST = SOUND_EVENTS.register("entity.seed.unstable_burst", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEED_UNSTABLE_FAIL = SOUND_EVENTS.register("entity.seed.unstable_fail", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEED_UNSTABLE_FLY = SOUND_EVENTS.register("entity.seed.unstable_fly", SoundEvent::createVariableRangeEvent);


    public static final DeferredHolder<SoundEvent, SoundEvent> LANTERN_HURT = SOUND_EVENTS.register("entity.lantern.hurt", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> LANTERN_IDLE = SOUND_EVENTS.register("entity.lantern.idle", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> LANTERN_SHED = SOUND_EVENTS.register("entity.lantern.shed", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> LANTERN_SPAWN = SOUND_EVENTS.register("entity.lantern.spawn", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> LANTERN_TELEPORT = SOUND_EVENTS.register("entity.lantern.teleport", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> RESPITE_TOTEM_ACTIVATE = SOUND_EVENTS.register("entity.respite_totem.activate", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> RESPITE_TOTEM_FLOAT = SOUND_EVENTS.register("entity.respite_totem.float", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> RESPITE_TOTEM_SHATTER = SOUND_EVENTS.register("entity.respite_totem.shatter", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> RESPITE_TOTEM_SPAWN = SOUND_EVENTS.register("entity.respite_totem.spawn", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> PULL = SOUND_EVENTS.register("item.magnet.pull", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGNET_FAIL = SOUND_EVENTS.register("item.magnet.fail", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGNET_SUCCESS = SOUND_EVENTS.register("item.magnet.success", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<SoundEvent, SoundEvent> POLAR_CHARGE = SOUND_EVENTS.register("block.polar.charge", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> POLAR_COOL = SOUND_EVENTS.register("block.polar.cool", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> POLAR_EMERGE = SOUND_EVENTS.register("block.polar.emerge", SoundEvent::createVariableRangeEvent);

}
