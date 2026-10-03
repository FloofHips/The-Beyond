package com.thebeyond.api.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Lookup over Beyond's blocks without exposing BeyondBlocks, usable from common setup on. */
@ApiStatus.Experimental
public final class BeyondBlockRegistry {
    private BeyondBlockRegistry() {}

    /** Block at {@code the_beyond:<path>}, or {@code null} if not registered. */
    @Nullable
    public static Block get(String path) {
        return BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, path));
    }

    /** Any-namespace pass-through to {@code BuiltInRegistries}. */
    @Nullable
    public static Block get(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id);
    }
}
