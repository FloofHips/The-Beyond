package com.thebeyond.data;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.data.BeyondDataMapTypes;
import com.thebeyond.common.data.ProjectorTexture;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.DataMapProvider;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class BeyondDataMaps extends DataMapProvider {
    protected BeyondDataMaps(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput, lookupProvider);
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        // Ornament and hand are left/right halves of one image; the Regions tile it.
        ResourceLocation keepsake = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "memory/keepsake");
        builder(BeyondDataMapTypes.PROJECTOR_TEXTURE)
                .add(BeyondItems.REMEMBRANCE_ORNAMENT.getKey(),
                        new ProjectorTexture(tex("ornament"), new ProjectorTexture.Region(0f, 0f, 0.5f, 1f), Optional.of(keepsake), 1f), false)
                .add(BeyondItems.REMEMBRANCE_HAND.getKey(),
                        new ProjectorTexture(tex("hand"), new ProjectorTexture.Region(0.5f, 0f, 1f, 1f), Optional.of(keepsake), 1f), false);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/projector/" + name + ".png");
    }
}
