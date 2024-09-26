package com.thebeyond.client.model;

import com.thebeyond.TheBeyond;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public class BeyondModelLayers {
    public static final ModelLayerLocation ENDERDROP_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enderdrop_layer"), "main");

    public static final ModelLayerLocation ENDERGLOP_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "enderglop_layer"), "main");
}
