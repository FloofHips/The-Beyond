package com.thebeyond.common.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public class BeyondToolTiers {
    public static final Tier BRITTLE_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            250,
            6.0F,
            2.0F,
            2,
            () -> Ingredient.of(BeyondItems.BRITTLE_METAL_SHEET)
    );
}
