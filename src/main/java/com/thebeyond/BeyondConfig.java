package com.thebeyond;

import com.thebeyond.common.knowledge.KnowledgeMode;
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

    /**
     * Master toggle for the "hide undiscovered content" system — Farlands
     * biomes, structures, items, and creative-tab entries are invisible
     * until the player reaches the corresponding region.
     * <p>When {@code false}, everything is shown to every player (vanilla-
     * behaviour fallback). Default {@code true}; the intended behaviour is
     * "hide until reached".
     * <p>Consumed by {@link com.thebeyond.common.knowledge.BeyondKnowledge#gateEnabled()}.
     */
    public static ModConfigSpec.BooleanValue HIDE_UNDISCOVERED_CONTENT;

    /**
     * Which players share knowledge with whom. See
     * {@link com.thebeyond.common.knowledge.KnowledgeMode} for the three
     * modes. Default {@code PER_PLAYER} — on a dedicated server each player
     * has their own progression. Admins can switch to {@code SHARED_WORLD}
     * to make all players see everything the moment anyone discovers it,
     * or {@code PER_PLAYER_WITH_IMPORT} to let players carry over knowledge
     * from past single-player worlds.
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
