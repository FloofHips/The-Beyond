package com.thebeyond.registers;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.EventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class RegisterEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TheBeyond.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<EnderglopEntity>> ENDERGLOP =
            ENTITY_TYPES.register("enderglop",
            () -> EntityType.Builder.of(EnderglopEntity::new, MobCategory.MONSTER)
                    .sized(0.5F, 0.5F)
                    .build(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enderglop").toString()));

    public static void register(EventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

}
