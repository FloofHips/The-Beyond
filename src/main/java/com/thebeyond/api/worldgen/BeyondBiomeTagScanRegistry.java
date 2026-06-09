package com.thebeyond.api.worldgen;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Extra biome tags addons register for Beyond's End auto-discovery, beyond the
 *  built-in vanilla/convention tags. */
@ApiStatus.Experimental
public final class BeyondBiomeTagScanRegistry {
    private static final Set<TagKey<Biome>> EXTRA_TAGS = new CopyOnWriteArraySet<>();

    private BeyondBiomeTagScanRegistry() {}

    public static void registerScanTag(TagKey<Biome> tag) {
        EXTRA_TAGS.add(tag);
    }

    public static Set<TagKey<Biome>> getRegisteredTags() {
        return Set.copyOf(EXTRA_TAGS);
    }
}
