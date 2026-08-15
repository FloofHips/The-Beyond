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
        ResourceLocation punishment = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "memory/punishment");
        ResourceLocation prison = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "memory/prison");
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "memory/key");
        ResourceLocation history = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "memory/history");

        builder(BeyondDataMapTypes.PROJECTOR_TEXTURE)

                .add(BeyondItems.REMEMBRANCE_BEADS.getKey(),
                        new ProjectorTexture(tex("beads_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(prison), 1f), false)
                .add(BeyondItems.REMEMBRANCE_HAND.getKey(),
                        new ProjectorTexture(tex("hand_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(prison), 1f), false)
                .add(BeyondItems.REMEMBRANCE_HOME.getKey(),
                        new ProjectorTexture(tex("home_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(prison), 1f), false)
                .add(BeyondItems.REMEMBRANCE_HORN.getKey(),
                        new ProjectorTexture(tex("horn_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(prison), 1f), false)

                .add(BeyondItems.REMEMBRANCE_BRACE.getKey(),
                        new ProjectorTexture(tex("brace_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(punishment), 1f), false)
                .add(BeyondItems.REMEMBRANCE_IDOL.getKey(),
                        new ProjectorTexture(tex("idol_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(punishment), 1f), false)
                .add(BeyondItems.REMEMBRANCE_LIFE.getKey(),
                        new ProjectorTexture(tex("life_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(punishment), 1f), false)
                .add(BeyondItems.REMEMBRANCE_RING.getKey(),
                        new ProjectorTexture(tex("ring_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(punishment), 1f), false)

                .add(BeyondItems.REMEMBRANCE_ORNAMENT.getKey(),
                        new ProjectorTexture(tex("ornament_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(key), 1f), false)
                .add(BeyondItems.REMEMBRANCE_MOUNT.getKey(),
                        new ProjectorTexture(tex("mount_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(key), 1f), false)
                .add(BeyondItems.REMEMBRANCE_EYE.getKey(),
                        new ProjectorTexture(tex("eye_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(key), 1f), false)
                .add(BeyondItems.REMEMBRANCE_MEMORY.getKey(),
                        new ProjectorTexture(tex("memory_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(key), 1f), false)

                .add(BeyondItems.REMEMBRANCE_BROCHE.getKey(),
                        new ProjectorTexture(tex("broche_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(history), 1f), false)
                .add(BeyondItems.REMEMBRANCE_CLOTH.getKey(),
                        new ProjectorTexture(tex("cloth_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(history), 1f), false)
                .add(BeyondItems.REMEMBRANCE_LACE.getKey(),
                        new ProjectorTexture(tex("lace_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(history), 1f), false)
                .add(BeyondItems.REMEMBRANCE_SPIKE.getKey(),
                        new ProjectorTexture(tex("spike_remembrance"), new ProjectorTexture.Region(0f, 0f, 1f, 1f), Optional.of(history), 1f), false);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/projection/" + name + ".png");
    }
}
