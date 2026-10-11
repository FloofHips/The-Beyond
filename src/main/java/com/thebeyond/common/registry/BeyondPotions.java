package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondPotions {
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(BuiltInRegistries.POTION, TheBeyond.MODID);

    /** No vanilla effects, which would ignore the crowd cap, the capped effect is applied on break instead. */
    public static final DeferredHolder<Potion, Potion> DEAFENING = POTIONS.register("deafening", () -> new Potion("deafening"));
}
