package com.thebeyond.common.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.google.common.collect.Sets;
import com.thebeyond.common.item.AirPlaceableBlockItem;
import com.thebeyond.common.item.AlsoPlaceableOnFluidBlockItem;
import com.thebeyond.common.item.MagnetItem;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SHROUD_ARMOR = ARMOR_MATERIALS.register("shroud",
            () -> new ArmorMaterial(
                    Map.of(
                            ArmorItem.Type.BOOTS, 1,
                            ArmorItem.Type.LEGGINGS, 1,
                            ArmorItem.Type.CHESTPLATE, 3,
                            ArmorItem.Type.HELMET, 1
                    ),
                    20,
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ARMOR_EQUIP_LEATHER.value()),
                    () -> Ingredient.of(BeyondItems.ABYSSAL_SHROUD.get()),
                    List.of(
                            new ArmorMaterial.Layer(
                                    ResourceLocation.fromNamespaceAndPath(MODID, "shroud")
                            )
                    ),
                    0.0F,
                    0.0F
            ));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ANCHOR_ARMOR = ARMOR_MATERIALS.register("anchor",
            () -> new ArmorMaterial(
                    Map.of(
                            ArmorItem.Type.BOOTS, 2,
                            ArmorItem.Type.LEGGINGS, 4,
                            ArmorItem.Type.CHESTPLATE, 5,
                            ArmorItem.Type.HELMET, 3
                    ),
                    20,
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ARMOR_EQUIP_NETHERITE.value()),
                    () -> Ingredient.of(BeyondItems.FERROPETAL.get()),
                    List.of(
                            new ArmorMaterial.Layer(
                                    ResourceLocation.fromNamespaceAndPath(MODID, "anchor")
                            )
                    ),
                    0.5F,
                    0.1F
            ));
}
