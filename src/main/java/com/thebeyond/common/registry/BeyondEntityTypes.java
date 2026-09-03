package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.*;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TheBeyond.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<LanternEntity>> LANTERN =
            ENTITY_TYPES.register("lantern",
                    () -> EntityType.Builder.of(LanternEntity::new, MobCategory.CREATURE)
                            .sized(1F, 1F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "lantern").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<AbyssalNomadEntity>> ABYSSAL_NOMAD =
            ENTITY_TYPES.register("abyssal_nomad",
                    () -> EntityType.Builder.of(AbyssalNomadEntity::new, MobCategory.CREATURE)
                            .sized(1.5F, 4F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "abyssal_nomad").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<TotemOfRespiteEntity>> TOTEM_OF_RESPITE =
            ENTITY_TYPES.register("totem_of_respite",
                    () -> EntityType.Builder.<TotemOfRespiteEntity>of(TotemOfRespiteEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.7F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "totem_of_respite").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<GravistarEntity>> GRAVISTAR =
            ENTITY_TYPES.register("gravistar",
                    () -> EntityType.Builder.<GravistarEntity>of(GravistarEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "gravistar").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<EnderglopEntity>> ENDERGLOP =
            ENTITY_TYPES.register("enderglop",
            () -> EntityType.Builder.of(EnderglopEntity::new, MobCategory.MONSTER)
                    .sized(0.5F, 0.5F)
                    .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enderglop").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<EnadrakeEntity>> ENADRAKE =
            ENTITY_TYPES.register("enadrake",
                    () -> EntityType.Builder.of(EnadrakeEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 0.9F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enadrake").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<EnatiousTotemEntity>> ENATIOUS_TOTEM =
            ENTITY_TYPES.register("enatious_totem",
                    () -> EntityType.Builder.of(EnatiousTotemEntity::new, MobCategory.MISC)
                            .sized(1.5F, 3F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enatious_totem").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<StalkerEntity>> STALKER =
            ENTITY_TYPES.register("stalker",
                    () -> EntityType.Builder.of(StalkerEntity::new, MobCategory.CREATURE)
                            .sized(1, 1)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "stalker").toString()));
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

    public static final DeferredHolder<EntityType<?>, EntityType<RisingBlockEntity>> RISING_BLOCK =
            ENTITY_TYPES.register("rising_block",
                    () -> EntityType.Builder.<RisingBlockEntity>of(RisingBlockEntity::new, MobCategory.MISC)
                            .sized(1, 1)
                            .clientTrackingRange(4)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "rising_block").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<BrubbleEntity>> BRUBBLE =
            ENTITY_TYPES.register("brubble",
                    () -> EntityType.Builder.<BrubbleEntity>of(BrubbleEntity::new, MobCategory.MONSTER)
                            .sized(0.9f, 0.9f)
                            .clientTrackingRange(4)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "brubble").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<SmokeFuseEntity>> SMOKE_FUSE =
            ENTITY_TYPES.register("smoke_fuse",
                    () -> EntityType.Builder.<SmokeFuseEntity>of(SmokeFuseEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "smoke_fuse").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<CoilEntity>> COILED_STALK =
            ENTITY_TYPES.register("coiled_stalk",
                    () -> EntityType.Builder.<CoilEntity>of(CoilEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "coiled_stalk").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<LivingBlock>> TEST_BLOCK =
            ENTITY_TYPES.register("test_block",
                    () -> EntityType.Builder.<LivingBlock>of(LivingBlock::new, MobCategory.MISC)
                            .sized(1, 1)
                            .clientTrackingRange(4)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "test_block").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<BeadEntity>> BEAD =
            ENTITY_TYPES.register("bead_entity",
                    () -> EntityType.Builder.<BeadEntity>of(BeadEntity::new, MobCategory.MISC)
                            .sized(1, 1)
                            .clientTrackingRange(4)
                            .updateInterval(1)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "bead_entity").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<LivingBlock>> ENTROPIC_BLOCK =
            ENTITY_TYPES.register("entropic_block",
                    () -> EntityType.Builder.<LivingBlock>of(LivingBlock::new, MobCategory.MISC)
                            .sized(1, 1)
                            .clientTrackingRange(4)
                            .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "entropic_block").toString()));
}
