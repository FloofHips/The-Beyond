package com.thebeyond.common.activation;

import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Static API for reading and writing per-chunk activation state.
 *
 * <p><b>Use cases</b> (none wired yet — this is scaffolding per
 * {@code IMPLEMENTATION_PLAN.md §2.1}):
 * <ul>
 *   <li>Polar Antenna chain — mark the chain as activated once a player
 *       triggers the first antenna; subsequent antennae in the chain read
 *       {@link #isActivated} to know whether to open their pillar core.</li>
 *   <li>Bismuth pool — once frozen by an ice block, mark the pool so the
 *       {@code randomTick} that re-melts exposed Molten Bismuth skips.</li>
 *   <li>Perka Stalk triggered — record which stalks have already extended
 *       to prevent a re-trigger on every proximity check.</li>
 *   <li>Legacy Grove dig — track which buried-loot tiles have been
 *       excavated, so loot tables don't re-roll.</li>
 * </ul>
 *
 * <p><b>Why static</b>: callers are typically {@code Block.useOn}, entity
 * AI, or feature placement — none of them hold a reference to an activation
 * manager. A static entry point is the lowest-friction call site and mirrors
 * how {@link com.thebeyond.common.worldgen.BeyondEndChunkGenerator#computeWrappedCoords}
 * is reachable.
 *
 * <p><b>Chunk access strategy</b>: mirrors the pattern used by
 * {@code ModGameEvents#getChunkData} — read through
 * {@link ServerLevel#getChunkSource()} without forcing load. Activation
 * state is inherently tied to the player's presence, so if the chunk isn't
 * resident we treat the query as "not activated" and mutations as no-ops.
 *
 * <p><b>Thread-safety</b>: all reasonable callers (block tick, entity tick,
 * server-tick event) run on the server thread. The underlying
 * {@link java.util.HashSet} in {@link ActivationChunkData} is not
 * thread-safe; we don't pay a synchronization cost because we don't need to.
 *
 * <p><b>Not wired yet</b>: the attachment is registered in
 * {@link BeyondAttachments#ACTIVATION_DATA} so save/load works the moment
 * anyone calls these methods. No existing code consults this state, so
 * adding it is a no-op for the running mod.
 */
public final class BeyondActivation {

    private BeyondActivation() {}

    // ---------------------------------------------------------------------
    //  Well-known kinds. String IDs rather than an enum so content sprints
    //  can add new kinds without touching this class.
    // ---------------------------------------------------------------------

    /** Activation kind used by Polar Antennae once a chain has fired. */
    public static final ResourceLocation POLAR_CHAIN =
            ResourceLocation.fromNamespaceAndPath("the_beyond", "polar_chain");

    /** Activation kind used by Bismuth pools once frozen. */
    public static final ResourceLocation BISMUTH_FROZEN =
            ResourceLocation.fromNamespaceAndPath("the_beyond", "bismuth_frozen");

    /** Activation kind used by Perka Stalks after their first extension. */
    public static final ResourceLocation PERKA_TRIGGERED =
            ResourceLocation.fromNamespaceAndPath("the_beyond", "perka_triggered");

    /** Activation kind used by Legacy Grove tiles after excavation. */
    public static final ResourceLocation LEGACY_EXCAVATED =
            ResourceLocation.fromNamespaceAndPath("the_beyond", "legacy_excavated");

    // ---------------------------------------------------------------------
    //  Core API
    // ---------------------------------------------------------------------

    /**
     * @return {@code true} if the given block position is marked activated
     * for this kind. Returns {@code false} when the chunk is not loaded
     * (callers deciding gameplay outcomes on unloaded chunks is itself a
     * design smell — we choose not to force-load).
     */
    public static boolean isActivated(ServerLevel level, BlockPos pos, ResourceLocation kind) {
        ChunkAccess chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
        if (chunk == null) return false;
        ActivationChunkData data = chunk.getData(BeyondAttachments.ACTIVATION_DATA);
        return data.isActivated(kind, ActivationChunkData.packXYZ(pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * Mark the given position as activated. No-op on unloaded chunks.
     * @return {@code true} if state actually changed.
     */
    public static boolean markActivated(ServerLevel level, BlockPos pos, ResourceLocation kind) {
        ChunkAccess chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
        if (chunk == null) return false;
        ActivationChunkData data = chunk.getData(BeyondAttachments.ACTIVATION_DATA);
        boolean changed = data.setActivated(kind, ActivationChunkData.packXYZ(pos.getX(), pos.getY(), pos.getZ()));
        if (changed) chunk.setUnsaved(true);
        return changed;
    }

    /**
     * Unmark the given position. No-op on unloaded chunks.
     * @return {@code true} if state actually changed.
     */
    public static boolean clearActivated(ServerLevel level, BlockPos pos, ResourceLocation kind) {
        ChunkAccess chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
        if (chunk == null) return false;
        ActivationChunkData data = chunk.getData(BeyondAttachments.ACTIVATION_DATA);
        boolean changed = data.clearActivated(kind, ActivationChunkData.packXYZ(pos.getX(), pos.getY(), pos.getZ()));
        if (changed) chunk.setUnsaved(true);
        return changed;
    }
}
