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

    public static final Supplier<PlayerTrigger> BEFRIEND_LANTERN = TRIGGERS.register("befriend_lantern", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> BRUSH_LANTERN = TRIGGERS.register("brush_lantern", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> OBTAIN_LIVE_FLAME = TRIGGERS.register("obtain_live_flame", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> OBTAIN_LIVID_FLAME = TRIGGERS.register("obtain_livid_flame", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> LIGHT_BONFIRE = TRIGGERS.register("light_bonfire", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> USE_TOTEM = TRIGGERS.register("use_totem", PlayerTrigger::new);

    public static final Supplier<PlayerTrigger> GIVE_REMEMBRANCE = TRIGGERS.register("give_remembrance", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> RIDE_NOMAD = TRIGGERS.register("ride_nomad", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> FOUNTAIN_OFFERING = TRIGGERS.register("fountain_offering", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> WALK_AURORACITE = TRIGGERS.register("walk_auroracite", PlayerTrigger::new);

    public static final Supplier<PlayerTrigger> MIGRATION_STORM = TRIGGERS.register("migration_storm", PlayerTrigger::new);

    public static final Supplier<PlayerTrigger> GIFT_ENADRAKE = TRIGGERS.register("gift_enadrake", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> GIFT_RARE_ENADRAKE = TRIGGERS.register("gift_rare_enadrake", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> COMPLETE_REFUGE = TRIGGERS.register("complete_refuge", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> USE_REFUGE = TRIGGERS.register("use_refuge", PlayerTrigger::new);

    public static final Supplier<PlayerTrigger> FULL_POWER_MAGNET = TRIGGERS.register("full_power_magnet", PlayerTrigger::new);

    public static final Supplier<PlayerTrigger> GEYSER = TRIGGERS.register("geyser", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> CASTING = TRIGGERS.register("casting", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> SNAPSHOT = TRIGGERS.register("snapshot", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> FREEZE = TRIGGERS.register("freeze", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> BRITTLE_RAIN = TRIGGERS.register("brittle_rain", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> MEMORY_FULL = TRIGGERS.register("memory_full", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> FILL_PROJECTOR = TRIGGERS.register("fill_projector", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> DISCOVER_PROJECTION = TRIGGERS.register("discover_projection", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> DISCOVER_ALL_PROJECTION_0 = TRIGGERS.register("discover_projection_0", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> DISCOVER_ALL_PROJECTION_1 = TRIGGERS.register("discover_projection_1", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> DISCOVER_ALL_PROJECTION_2 = TRIGGERS.register("discover_projection_2", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> DISCOVER_ALL_PROJECTION_3 = TRIGGERS.register("discover_projection_3", PlayerTrigger::new);

    public static final Supplier<PlayerTrigger> ENCOUNTER_STALKER = TRIGGERS.register("encounter_stalker", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> MISSED_ME = TRIGGERS.register("missed_me", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> WALKING_THE_ROPE = TRIGGERS.register("walk_the_rope", PlayerTrigger::new);

    public static final Supplier<PlayerTrigger> COLLECT_PEARL = TRIGGERS.register("collect_pearl", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> STRIP_MIRROR = TRIGGERS.register("strip_mirror", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> TAME_BAUBLE = TRIGGERS.register("tame_bauble", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> BUCKET_TRINKET = TRIGGERS.register("bucket_trinket", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> SELECT_TRINKET = TRIGGERS.register("select_trinket", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> WAX_TRINKET = TRIGGERS.register("wax_trinket", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> RAKE_NACRE = TRIGGERS.register("rake_nacre", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> CLINAMEN = TRIGGERS.register("clinamen", PlayerTrigger::new);
    public static final Supplier<PlayerTrigger> CREATE_BAUBLE = TRIGGERS.register("create_bauble", PlayerTrigger::new);
}
