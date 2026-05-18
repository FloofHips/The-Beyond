package com.thebeyond.api.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Thin lookup over Beyond's block registry (avoids leaking the internal
 *  {@code BeyondBlocks}). Call from {@code FMLCommonSetupEvent} onwards. */
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
