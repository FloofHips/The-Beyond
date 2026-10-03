package com.thebeyond;

import com.thebeyond.common.awareness.AwarenessMode;
import net.neoforged.neoforge.common.ModConfigSpec;


public class BeyondConfig {
    public static ModConfigSpec COMMON_CONFIG;
    public static ModConfigSpec CLIENT_CONFIG;

    // Override End fog with Beyond's custom atmospheric fog.
    public static ModConfigSpec.BooleanValue ENABLE_CUSTOM_FOG;
    // Tweak the lightmap depending on the Beyond's weather system.
    public static ModConfigSpec.BooleanValue CLAMP_WEATHER_LIGHT_MAP;
    // Override End fog with Beyond's sky renderer.
    public static ModConfigSpec.BooleanValue ENABLE_CUSTOM_SKY;
    public static ModConfigSpec.BooleanValue ENABLE_MAIN_ISLAND_CLOUDS;

    public static ModConfigSpec.BooleanValue MIRROR_OCCLUSION_MODEL_BASED;

    public static ModConfigSpec.BooleanValue HIDE_UNDISCOVERED_CONTENT;

    public static ModConfigSpec.EnumValue<AwarenessMode> AWARENESS_MODE;

    public static ModConfigSpec.BooleanValue DROP_TOTEM_OF_RESPITE;

    public static ModConfigSpec.BooleanValue DEAFENING_DISENGAGE;
    public static ModConfigSpec.IntValue DEAFENING_LOCAL_CAP;
    public static ModConfigSpec.IntValue DEAFENING_GLOBAL_CAP;
    public static ModConfigSpec.IntValue WARDEN_ENRAGE_ANGER;
    public static ModConfigSpec.IntValue WARDEN_SMELL_RADIUS;

    public static ModConfigSpec.IntValue VOID_SEA_OFFSET;

    static {

        ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

        // optional discovery gating that hides content until players find it, off by default, uncomment to enable
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

        COMMON_BUILDER.comment("Deafening / FOV mob-stealth").push("deafening");
        DEAFENING_DISENGAGE = COMMON_BUILDER
                .comment("If true, a deafened mob DROPS an already-acquired player target when the",
                        "player leaves its frontal FOV cone or line of sight (checked twice a second).",
                        "If false, deafening only blocks fresh acquisition. Default: true")
                .define("disengageOnConeExit", true);
        DEAFENING_LOCAL_CAP = COMMON_BUILDER
                .comment("Max eligible (non-immune) mobs within a deafening burst's radius.",
                        "Above this, the burst deafens NOBODY (a crowd notices you anyway);",
                        "the screech and startle still happen. Default: 16")
                .defineInRange("localCap", 16, 0, 1024);
        DEAFENING_GLOBAL_CAP = COMMON_BUILDER
                .comment("Hard safety ceiling on eligible mobs scanned per burst (performance guardrail).",
                        "Default: 64")
                .defineInRange("globalCap", 64, 0, 4096);
        WARDEN_ENRAGE_ANGER = COMMON_BUILDER
                .comment("Anger applied to a Warden when a deafening potion breaks near it.",
                        "80 is vanilla's 'angry' threshold (roars + pursues). Default: 80")
                .defineInRange("wardenEnrageAnger", 80, 0, 150);
        WARDEN_SMELL_RADIUS = COMMON_BUILDER
                .comment("A deafened Warden still senses the player by smell within this radius (blocks).",
                        "Beyond it, the player's vibration-driven anger is suppressed. Default: 4")
                .defineInRange("wardenSmellRadius", 4, 0, 64);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.comment("Create: Aeronautics void sea").push("aeronautics");
        VOID_SEA_OFFSET = COMMON_BUILDER
                .comment("Void sea height relative to the End floor (the auroracite layer at the world's min-Y), in blocks.",
                        "Positive raises the sea; the void-death line stays put. Negative lowers the sea below the floor",
                        "AND lowers the void-death line in step (same 64-block buffer), so contraptions and pilots ride",
                        "the lowered sea instead of voiding out. Default: 2")
                .defineInRange("voidSeaOffsetAboveFloor", 2, -64, 256);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.comment("Gameplay").push("gameplay");
        DROP_TOTEM_OF_RESPITE = COMMON_BUILDER
                .comment("The totem of respite allows you to get your items back after death. Some mods will conflict with this",
                        "The config stops the totem from being obtained during the Bonfire ritual")
                .define("DropTotemOfRespite", true);
        COMMON_BUILDER.pop();

        COMMON_CONFIG = COMMON_BUILDER.build();

        ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

        CLIENT_BUILDER.comment("Visual settings for the End dimension").push("visuals");
        ENABLE_CUSTOM_FOG = CLIENT_BUILDER
                .comment("Enable custom atmospheric End fog.",
                        "When disabled, vanilla End fog is used (no custom distances or shape overrides).",
                        "Default: true")
                .translation(TheBeyond.MODID + ".config.enable_custom_fog")
                .define("enableCustomFog", true);

        ENABLE_CUSTOM_SKY = CLIENT_BUILDER
                .comment("Enable custom sky renderer.",
                        "When disabled, vanilla End sky renderer is used.",
                        "Default: true")
                .translation(TheBeyond.MODID + ".config.enable_custom_sky")
                .define("enableCustomSky", true);

        CLAMP_WEATHER_LIGHT_MAP = CLIENT_BUILDER
                .comment("Clamp light map values.",
                        "When enabled, the light map is clamped. Useful if you use mods that change the lightmap as well.",
                        "When the Beyond is paired with other mods, there is a chance of the lightmap overspilling into green instead of purple.",
                        "Default: false")
                .translation(TheBeyond.MODID + ".config.clamp_lightmap")
                .define("clampLightMap", false);

        ENABLE_MAIN_ISLAND_CLOUDS = CLIENT_BUILDER
                .comment("Enable swirling clouds in the main island.",
                        "Default: true")
                .translation(TheBeyond.MODID + ".config.enable_swirling_clouds")
                .define("enableSwirlingClouds", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.comment("Pearl mirror reflection").push("mirror");
        MIRROR_OCCLUSION_MODEL_BASED = CLIENT_BUILDER
                .comment("Mirror occlusion shape: true = block's real model (fences show gaps); false = cheaper AABB box. Default: true")
                .translation(TheBeyond.MODID + ".config.mirror_occlusion_model_based")
                .define("occlusionModelBased", true);
        CLIENT_BUILDER.pop();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }

}
