package com.thebeyond;

import net.neoforged.neoforge.common.ModConfigSpec;


public class BeyondConfig {
    public static ModConfigSpec COMMON_CONFIG;
    public static ModConfigSpec CLIENT_CONFIG;

    /**
     * Whether Beyond overrides the End dimension's fog distances with its custom
     * Y-dependent atmospheric fog. When disabled, vanilla End fog behavior is used.
     * <p>Consumed by {@link com.thebeyond.mixin.client.FogRendererMixin} and the
     * {@code onRenderFog} handler in {@code ModClientEvents}.
     */
    public static ModConfigSpec.BooleanValue ENABLE_CUSTOM_FOG;

    static {

        ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

        COMMON_CONFIG = COMMON_BUILDER.build();

        ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

        CLIENT_BUILDER.comment("Visual settings for the End dimension").push("visuals");
        ENABLE_CUSTOM_FOG = CLIENT_BUILDER
                .comment("Enable Beyond's custom atmospheric End fog.",
                        "When disabled, vanilla End fog is used (no custom distances or shape overrides).",
                        "Default: true")
                .translation(TheBeyond.MODID + ".config.enable_custom_fog")
                .define("enableCustomFog", true);
        CLIENT_BUILDER.pop();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }

}
