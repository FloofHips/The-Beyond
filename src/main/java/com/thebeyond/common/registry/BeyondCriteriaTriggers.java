package com.thebeyond.common.registry;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondCriteriaTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, MODID);

    public static final Supplier<PlayerTrigger> OBTAIN_LIVE_FLAME = TRIGGERS.register("obtain_live_flame", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> LIGHT_BONFIRE = TRIGGERS.register("light_bonfire", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> GIVE_REMEMBRANCE = TRIGGERS.register("give_remembrance", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> RIDE_NOMAD = TRIGGERS.register("ride_nomad", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> FOUNTAIN_OFFERING = TRIGGERS.register("fountain_offering", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> WALK_VOID_RIVER = TRIGGERS.register("walk_void_river", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> RIDE_LANTERN_THUNDER = TRIGGERS.register("ride_lantern_thunder", PlayerTrigger::new);
}
