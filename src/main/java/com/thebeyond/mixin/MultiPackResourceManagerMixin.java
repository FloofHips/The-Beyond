package com.thebeyond.mixin;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.compat.EndDimensionFilteringPackResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps foreign packs in {@link EndDimensionFilteringPackResources} so Beyond's
 * {@code beyond_terrain} pack wins the {@code dimension/the_end.json} and
 * {@code dimension_type/the_end.json} slots. Everything else (biomes, noise settings,
 * features, structures) passes through unchanged.
 *
 * <p>Skips Beyond's own packs and already-wrapped packs. No-ops when Beyond's pack is
 * absent (soup-mode fallback).</p>
 */
@Mixin(MultiPackResourceManager.class)
public class MultiPackResourceManagerMixin {

    private static final String BEYOND_PACK_ID_PREFIX = "mod/" + TheBeyond.MODID + ":";
    private static final String BEYOND_MAIN_PACK_ID = BEYOND_PACK_ID_PREFIX + "beyond_terrain";

    private static final ResourceLocation END_DIMENSION = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension/the_end.json");
    private static final ResourceLocation END_DIMENSION_TYPE = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension_type/the_end.json");

    @ModifyVariable(
            method = "<init>(Lnet/minecraft/server/packs/PackType;Ljava/util/List;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static List<PackResources> the_beyond$filterEndDimensionOverrides(List<PackResources> packs) {
        if (packs == null || packs.isEmpty()) {
            return packs;
        }

        // Bail if Beyond's main pack isn't present — fall through to "soup mode".
        boolean beyondPresent = false;
        for (PackResources pr : packs) {
            if (BEYOND_MAIN_PACK_ID.equals(pr.packId())) {
                beyondPresent = true;
                break;
            }
        }
        if (!beyondPresent) {
            return packs;
        }

        List<PackResources> filtered = new ArrayList<>(packs.size());
        List<String> wrappedIds = new ArrayList<>();
        for (PackResources pr : packs) {
            String id = pr.packId();

            // Don't wrap any Beyond-owned pack (beyond_terrain + compat sidecars).
            if (id != null && id.startsWith(BEYOND_PACK_ID_PREFIX)) {
                filtered.add(pr);
                continue;
            }
            // Don't double-wrap.
            if (pr instanceof EndDimensionFilteringPackResources) {
                filtered.add(pr);
                continue;
            }

            // Wrap packs that ship either contested dimension file. Other foreign content
            // (noise settings, biomes, features) is NOT a trigger and NOT hidden.
            boolean hasDimension = pr.getResource(PackType.SERVER_DATA, END_DIMENSION) != null;
            boolean hasDimensionType = pr.getResource(PackType.SERVER_DATA, END_DIMENSION_TYPE) != null;
            if (hasDimension || hasDimensionType) {
                filtered.add(new EndDimensionFilteringPackResources(pr));
                wrappedIds.add(id);
            } else {
                filtered.add(pr);
            }
        }

        if (!wrappedIds.isEmpty()) {
            TheBeyond.LOGGER.info(
                    "[TheBeyond] Beyond terrain owns the End — stripping dimension/the_end.json + dimension_type/the_end.json from {} foreign pack(s): {}",
                    wrappedIds.size(), wrappedIds);
        }
        return filtered;
    }
}
