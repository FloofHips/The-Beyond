package com.thebeyond.common.data;

import com.mojang.serialization.Codec;
import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.AdvancedDataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapValueMerger;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BeyondDataMapTypes {
    private BeyondDataMapTypes() {
    }

    /** Synced so the client, which resolves the texture, sees the mapping. */
    public static final DataMapType<Item, ProjectorTexture> PROJECTOR_TEXTURE =
            DataMapType.builder(
                            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "projector_texture"),
                            Registries.ITEM,
                            ProjectorTexture.CODEC)
                    .synced(ProjectorTexture.CODEC, false)
                    .build();

    /** Per type, names that hide an entity from mirrors and photos, lists from several datapacks add up. */
    public static final DataMapType<EntityType<?>, List<String>> NO_IMAGE_NAMES =
            AdvancedDataMapType.builder(
                            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "no_image_names"),
                            Registries.ENTITY_TYPE,
                            Codec.STRING.listOf())
                    .synced(Codec.STRING.listOf(), false)
                    .merger(DataMapValueMerger.listMerger())
                    .build();

    public static void onRegisterDataMaps(RegisterDataMapTypesEvent event) {
        event.register(PROJECTOR_TEXTURE);
        event.register(NO_IMAGE_NAMES);
    }

    public static @Nullable ProjectorTexture getProjectorTexture(ItemStack stack) {
        return stack.getItem().builtInRegistryHolder().getData(PROJECTOR_TEXTURE);
    }
}
