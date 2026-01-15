package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.item.components.ComponentCodecs;
import com.thebeyond.common.item.components.Components;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
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
