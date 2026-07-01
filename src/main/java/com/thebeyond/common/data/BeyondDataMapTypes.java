package com.thebeyond.common.data;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

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

    public static void onRegisterDataMaps(RegisterDataMapTypesEvent event) {
        event.register(PROJECTOR_TEXTURE);
    }

    public static @Nullable ProjectorTexture getProjectorTexture(ItemStack stack) {
        return stack.getItem().builtInRegistryHolder().getData(PROJECTOR_TEXTURE);
    }
}
