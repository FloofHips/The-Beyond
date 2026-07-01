package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.item.components.ComponentCodecs;
import com.thebeyond.common.item.components.Components;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TheBeyond.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Components.DynamicColorComponent>> COLOR_COMPONENT = COMPONENTS.registerComponentType(
            "color",
            builder -> builder
                    .persistent(ComponentCodecs.DYNAMIC_COLOR_CODEC)
                    .networkSynchronized(ComponentCodecs.DYNAMIC_COLOR_STREAM_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Components.SnapshotPixelsComponent>> SNAPSHOT_PIXELS = COMPONENTS.registerComponentType(
            "snapshot_pixels",
            builder -> builder
                    .persistent(ComponentCodecs.SNAPSHOT_PIXELS_CODEC)
                    .networkSynchronized(ComponentCodecs.SNAPSHOT_PIXELS_STREAM_CODEC)
                    .cacheEncoding()
    );

    /** The filter (data-driven grade id) a snapshot was created with; shown wherever the photo renders. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> SNAPSHOT_GRADE = COMPONENTS.registerComponentType(
            "snapshot_grade",
            builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
    );

    /** The filter a camera stamps onto the photos it takes. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> CAMERA_GRADE = COMPONENTS.registerComponentType(
            "camera_grade",
            builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
    );

    //public static final DeferredHolder<DataComponentType<?>, DataComponentType<Components.ColorComponent>> COLOR_COMPONENT = COMPONENTS.registerComponentType(
    //        "color",
    //        builder -> builder
    //                .persistent(ComponentCodecs.COLOR_COMPONENT_CODEC)
    //                .networkSynchronized(ComponentCodecs.COLOR_COMPONENT_STREAM_CODEC)
    //);
    //public static final DeferredHolder<DataComponentType<?>, DataComponentType<Components.AlphaComponent>> ALPHA_COMPONENT = COMPONENTS.registerComponentType(
    //        "alpha",
    //        builder -> builder
    //                .persistent(ComponentCodecs.ALPHA_COMPONENT_CODEC)
    //                .networkSynchronized(ComponentCodecs.ALPHA_COMPONENT_STREAM_CODEC)
    //);
    //public static final DeferredHolder<DataComponentType<?>, DataComponentType<Components.BrightnessComponent>> BRIGHTNESS_COMPONENT = COMPONENTS.registerComponentType(
    //        "brightness",
    //        builder -> builder
    //                .persistent(ComponentCodecs.BRIGHTNESS_COMPONENT_CODEC)
    //                .networkSynchronized(ComponentCodecs.BRIGHTNESS_COMPONENT_STREAM_CODEC)
    //);
}
