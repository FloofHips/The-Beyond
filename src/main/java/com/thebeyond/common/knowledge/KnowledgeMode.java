package com.thebeyond.common.knowledge;

/**
 * Knowledge-sharing scope, selected via {@code BeyondConfig}.
 * <ul>
 *   <li>{@link #PER_PLAYER} — discovery unlocks content only for the discovering player (default).</li>
 *   <li>{@link #SHARED_WORLD} — any player's discovery unlocks content for everyone.</li>
 *   <li>{@link #PER_PLAYER_WITH_IMPORT} — per-player, with an opt-in import from past worlds (import flow TODO).</li>
 * </ul>
 */
public enum KnowledgeMode {
    PER_PLAYER,
    SHARED_WORLD,
    PER_PLAYER_WITH_IMPORT
}
