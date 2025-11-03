package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.EventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TheBeyond.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<LanternEntity>> LANTERN =
            ENTITY_TYPES.register("lantern",
                    () -> EntityType.Builder.of(LanternEntity::new, MobCategory.AMBIENT)
                            .sized(1F, 1F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "lantern").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<EnderglopEntity>> ENDERGLOP =
            ENTITY_TYPES.register("enderglop",
            () -> EntityType.Builder.of(EnderglopEntity::new, MobCategory.MONSTER)
                    .sized(0.5F, 0.5F)
                    .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enderglop").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<EnadrakeEntity>> ENADRAKE =
            ENTITY_TYPES.register("enadrake",
                    () -> EntityType.Builder.of(EnadrakeEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.9F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enadrake").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<EnatiousTotemEntity>> ENATIOUS_TOTEM =
            ENTITY_TYPES.register("enatious_totem",
                    () -> EntityType.Builder.of(EnatiousTotemEntity::new, MobCategory.MISC)
                            .sized(1.5F, 3F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enatious_totem").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<KnockbackSeedEntity>> KNOCKBACK_SEED =
            ENTITY_TYPES.register("knockback_seed",
                    () -> EntityType.Builder.<KnockbackSeedEntity>of(KnockbackSeedEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(4)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "knockback_seed").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<PoisonSeedEntity>> POISON_SEED =
            ENTITY_TYPES.register("poison_seed",
                    () -> EntityType.Builder.<PoisonSeedEntity>of(PoisonSeedEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(4)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "poison_seed").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<UnstableSeedEntity>> UNSTABLE_SEED =
            ENTITY_TYPES.register("unstable_seed",
                    () -> EntityType.Builder.<UnstableSeedEntity>of(UnstableSeedEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(4)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "unstable_seed").toString()));
    public static void register(EventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

}
