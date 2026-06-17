package com.thebeyond;

import com.thebeyond.common.awareness.AwarenessMode;
import net.neoforged.neoforge.common.ModConfigSpec;


public class BeyondConfig {
    public static ModConfigSpec COMMON_CONFIG;
    public static ModConfigSpec CLIENT_CONFIG;

    // Override End fog with Beyond's Y-dependent atmospheric fog.
    public static ModConfigSpec.BooleanValue ENABLE_CUSTOM_FOG;

    // Hide progression-gated content until the player discovers it.
    public static ModConfigSpec.BooleanValue HIDE_UNDISCOVERED_CONTENT;

    // How discovery is shared between players.
    public static ModConfigSpec.EnumValue<AwarenessMode> AWARENESS_MODE;

    static {

        ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

        // Optional discovery gating (hide content until players find it). Off by default; uncomment to enable.
        /*
        COMMON_BUILDER.comment("Progression / discovery gating").push("awareness");
        HIDE_UNDISCOVERED_CONTENT = COMMON_BUILDER
                .comment("Hide progression-gated content (biomes, structures, items,",
                        "creative-tab entries, /locate targets, compass suggestions,",
                        "JEI/REI recipes) until the player personally discovers it.",
                        "When false, all content is visible regardless of progression.",
                        "Default: true")
                .translation(TheBeyond.MODID + ".config.hide_undiscovered_content")
                .define("hideUndiscoveredContent", true);
        AWARENESS_MODE = COMMON_BUILDER
                .comment("How awareness is shared between players on the server.",
                        "PER_PLAYER — each player has independent progression.",
                        "SHARED_WORLD — any player's discovery unlocks content for all.",
                        "PER_PLAYER_WITH_IMPORT — per-player, but players may import",
                        "                         awareness from other worlds (on login).",
                        "Default: PER_PLAYER")
                .translation(TheBeyond.MODID + ".config.awareness_mode")
                .defineEnum("awarenessMode", AwarenessMode.PER_PLAYER);
        COMMON_BUILDER.pop();
        */

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
