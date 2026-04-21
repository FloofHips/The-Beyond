package com.thebeyond;

import com.thebeyond.common.knowledge.KnowledgeMode;
import net.neoforged.neoforge.common.ModConfigSpec;


public class BeyondConfig {
    public static ModConfigSpec COMMON_CONFIG;
    public static ModConfigSpec CLIENT_CONFIG;

    /**
     * When true, Beyond overrides the End dimension's fog distances with its custom
     * Y-dependent atmospheric fog. When false, vanilla End fog is used.
     * Consumed by {@link com.thebeyond.mixin.client.FogRendererMixin} and {@code ModClientEvents}.
     */
    public static ModConfigSpec.BooleanValue ENABLE_CUSTOM_FOG;

    /**
     * Master toggle for progressive discovery: Farlands biomes, structures, items and
     * creative-tab entries are hidden until the player reaches the corresponding region.
     * When false, all content is visible regardless of progression.
     * Consumed by {@link com.thebeyond.common.knowledge.BeyondKnowledge#gateEnabled()}.
     */
    public static ModConfigSpec.BooleanValue HIDE_UNDISCOVERED_CONTENT;

    /**
     * Scope of knowledge sharing between players on a server. See
     * {@link com.thebeyond.common.knowledge.KnowledgeMode} for the available modes.
     */
    public static ModConfigSpec.EnumValue<KnowledgeMode> KNOWLEDGE_MODE;

    static {

        ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

        COMMON_BUILDER.comment("Progression / discovery gating").push("knowledge");
        HIDE_UNDISCOVERED_CONTENT = COMMON_BUILDER
                .comment("Hide Farlands/Wall/Beyond content (biomes, structures, items,",
                        "creative-tab entries, /locate targets, compass suggestions,",
                        "JEI/REI recipes) until the player personally reaches them.",
                        "When false, all content is visible regardless of progression.",
                        "Default: true")
                .translation(TheBeyond.MODID + ".config.hide_undiscovered_content")
                .define("hideUndiscoveredContent", true);
        KNOWLEDGE_MODE = COMMON_BUILDER
                .comment("How knowledge is shared between players on the server.",
                        "PER_PLAYER — each player has independent progression.",
                        "SHARED_WORLD — any player's discovery unlocks content for all.",
                        "PER_PLAYER_WITH_IMPORT — per-player, but players may import",
                        "                         knowledge from other worlds (on login).",
                        "Default: PER_PLAYER")
                .translation(TheBeyond.MODID + ".config.knowledge_mode")
                .defineEnum("knowledgeMode", KnowledgeMode.PER_PLAYER);
        COMMON_BUILDER.pop();

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
