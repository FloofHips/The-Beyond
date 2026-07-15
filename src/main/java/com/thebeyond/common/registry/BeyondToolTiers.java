package com.thebeyond.common.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public class BeyondToolTiers {
    public static final Tier BRITTLE_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            50,
            11f,
            0.5f,
            20,
            () -> Ingredient.of(BeyondItems.BRITTLE_METAL_SHEET)
    );
}
