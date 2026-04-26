package com.thebeyond.common.knowledge;

/** Knowledge-sharing scope, selected via {@code BeyondConfig}. */
public enum KnowledgeMode {
    /** Discovery unlocks content only for the discovering player (default). */
    PER_PLAYER,
    /** Any player's discovery unlocks content for everyone on the server. */
    SHARED_WORLD,
    /** Per-player, with an opt-in import from past worlds. */
    PER_PLAYER_WITH_IMPORT
}
