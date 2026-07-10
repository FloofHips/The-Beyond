package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondPotions {
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(BuiltInRegistries.POTION, TheBeyond.MODID);

    /**
     * Carries no vanilla effects: vanilla splash/AoE would hit players and ignore the crowd cap. The capped, mob-only
     * effect is applied on break by {@link com.thebeyond.common.event.BeyondDeafeningPotionEvents}; the name prefix drives the translation keys.
     */
    public static final DeferredHolder<Potion, Potion> DEAFENING = POTIONS.register("deafening", () -> new Potion("deafening"));
}
