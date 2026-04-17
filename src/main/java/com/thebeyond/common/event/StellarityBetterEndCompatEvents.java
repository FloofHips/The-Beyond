package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Bootstraps Stellarity's dragons_den chain on Beyond's End (with or without BetterEnd).
 *
 * <p>On the first server tick where the origin chunk is loaded:
 * <ol>
 *   <li>Places the center crystal at (0.5, 61, 0.5) so Stellarity's exit_portal predicate fires.</li>
 *   <li>Force-loads altar chunks so {@code /place template} doesn't silently fail on unloaded chunks.</li>
 *   <li>Force-loads + kills all 10 ring spike crystals (repeated sweep ticks 1–20 for async entity visibility).</li>
 * </ol>
 *
 * <p>Uses both {@code getChunk()} (synchronous gen to FULL) and {@code setChunkForced()} (persistent ticket)
 * — the first ensures entities exist, the second prevents eviction before Stellarity's mcfunctions run.</p>
 *
 * <p>Gated on: Stellarity loaded, {@link BeyondTerrainState#isActive()}, once per session.</p>
 */
@EventBusSubscriber(modid = TheBeyond.MODID)
public class StellarityBetterEndCompatEvents {

    private static final BlockPos BEDROCK_POS = new BlockPos(0, 60, 0);
    private static final double CRYSTAL_X = 0.5D;
    private static final double CRYSTAL_Y = 61.0D;
    private static final double CRYSTAL_Z = 0.5D;

    /** Search box for an existing crystal/marker at center (slightly wider than needed). */
    private static final AABB CENTER_SEARCH_BOX = new AABB(-3.0D, 58.0D, -3.0D, 4.0D, 66.0D, 4.0D);

    /** (x, z) centers of the 10 ring spikes from Stellarity's {@code ring.json}. */
    private static final int[][] STELLARITY_RING_POSITIONS = new int[][] {
            {  63,   0 }, {  50,  36 }, {  18,  59 }, { -19,  59 },
            { -51,  36 }, { -63,   0 }, { -51, -39 }, { -19, -60 },
            {  18, -60 }, {  50, -39 }
    };

    /** 2x2 chunk pad around the altar template anchor at (42, 67, -48). */
    private static final ChunkPos[] ALTAR_CHUNKS = new ChunkPos[] {
            new ChunkPos(2, -4),
            new ChunkPos(2, -3),
            new ChunkPos(3, -4),
            new ChunkPos(3, -3)
    };

    /** Ticks to hold forced-chunk tickets (~10s buffer for Stellarity's mcfunction chain). */
    private static final int UNFORCE_DELAY_TICKS = 200;

    /** One-shot flag — reset on ServerStoppedEvent so single-player world reloads re-check. */
    private static boolean placedThisSession = false;

    /** List of chunks we forced via {@link ServerLevel#setChunkForced} for later release. */
    private static final List<ChunkPos> forcedChunks = new ArrayList<>();

    /** Tick counter since placement; -1 = not yet placed or already released. */
    private static int tickSincePlacement = -1;

    /** Union of {@link #ALTAR_CHUNKS} and the chunks containing every ring spike. */
    private static ChunkPos[] altarAndRingChunks() {
        List<ChunkPos> out = new ArrayList<>();
        // Origin chunk: holds center crystal, bedrock, chest at (7,60,0).
        out.add(new ChunkPos(0, 0));
        // Altar template anchor + 2x2 pad.
        for (ChunkPos cp : ALTAR_CHUNKS) {
            if (!out.contains(cp)) out.add(cp);
        }
        // Chunks containing each ring spike so remove_crystals catches the pre-placed entities.
        for (int[] pos : STELLARITY_RING_POSITIONS) {
            ChunkPos cp = new ChunkPos(pos[0] >> 4, pos[1] >> 4);
            if (!out.contains(cp)) out.add(cp);
        }
        return out.toArray(new ChunkPos[0]);
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            return;
        }

        // Post-placement timer: count ticks since we pinned the chunks, release the tickets
        // once Stellarity's post_gen chain has had a chance to run.
        if (tickSincePlacement >= 0) {
            tickSincePlacement++;

            // Repeated kill sweep: worldgen entities may not be indexed for 5-15 ticks.
            // 10 AABB queries/tick is negligible cost.
            if (tickSincePlacement >= 1 && tickSincePlacement <= 20) {
                int lateKills = 0;
                for (int[] pos : STELLARITY_RING_POSITIONS) {
                    AABB spikeBox = new AABB(
                            pos[0] - 10.0D, 0.0D, pos[1] - 10.0D,
                            pos[0] + 10.0D, 256.0D, pos[1] + 10.0D);
                    for (EndCrystal ec : end.getEntitiesOfClass(EndCrystal.class, spikeBox)) {
                        ec.discard();
                        lateKills++;
                    }
                }
                if (lateKills > 0) {
                    TheBeyond.LOGGER.info(
                            "[TheBeyond] Delayed kill pass (tick {}): removed {} late-indexed ring crystal(s).",
                            tickSincePlacement, lateKills);
                }
            }

            if (tickSincePlacement >= UNFORCE_DELAY_TICKS) {
                releaseForcedChunks(end);
                tickSincePlacement = -1;
            }
        }

        if (placedThisSession) {
            return;
        }

        // Gate: Stellarity must be loaded. BetterEnd is optional — the force-load, ring
        // crystal kill, and altar chunk pinning are needed regardless of whether BetterEnd
        // is in the combo. Without Stellarity there's no dragons_den chain to bootstrap.
        if (!ModList.get().isLoaded("stellarity")) {
            placedThisSession = true;
            return;
        }

        // Gate: Beyond owns the End. In soup mode, leave foreign generators alone.
        if (!BeyondTerrainState.isActive()) {
            placedThisSession = true;
            return;
        }

        // Wait for the origin chunk to actually be loaded before we try to write into it.
        if (!end.hasChunk(0, 0)) {
            return;
        }

        // If Stellarity has already FULLY processed (marker entity with portal/marker tag
        // exists), the chain is done and we don't need to do anything.
        List<Entity> nearby = end.getEntitiesOfClass(Entity.class, CENTER_SEARCH_BOX);
        for (Entity e : nearby) {
            if (!(e instanceof EndCrystal)
                    && (e.getTags().contains("fe.exit_portal") || e.getTags().contains("stellarity.marker"))) {
                TheBeyond.LOGGER.info(
                        "[TheBeyond] Stellarity marker entity already present — skipping compat fix.");
                placedThisSession = true;
                return;
            }
        }

        // Force-load all ring/altar chunks: getChunk() for sync gen, setChunkForced() to pin.
        ChunkPos[] targets = altarAndRingChunks();
        for (ChunkPos cp : targets) {
            end.getChunk(cp.x, cp.z);
            if (end.setChunkForced(cp.x, cp.z, true)) {
                forcedChunks.add(cp);
            }
        }

        // Kill pre-existing ring crystals (entity manager may not have indexed them all yet).
        int ringCrystalsKilled = 0;
        for (int[] pos : STELLARITY_RING_POSITIONS) {
            AABB spikeBox = new AABB(
                    pos[0] - 10.0D, 0.0D, pos[1] - 10.0D,
                    pos[0] + 10.0D, 256.0D, pos[1] + 10.0D);
            for (EndCrystal ec : end.getEntitiesOfClass(EndCrystal.class, spikeBox)) {
                TheBeyond.LOGGER.debug(
                        "[TheBeyond] Killing ring crystal at ({}, {}, {}) near spike ({}, {})",
                        ec.getX(), ec.getY(), ec.getZ(), pos[0], pos[1]);
                ec.discard();
                ringCrystalsKilled++;
            }
        }
        if (ringCrystalsKilled > 0) {
            TheBeyond.LOGGER.info(
                    "[TheBeyond] Killed {} pre-existing ring crystal(s) at Stellarity spike positions.",
                    ringCrystalsKilled);
        }

        // Place center crystal only if one doesn't already exist (BetterEnd may have placed one).
        boolean centerCrystalExists = false;
        for (Entity e : nearby) {
            if (e instanceof EndCrystal ec) {
                double dy = ec.getY() - CRYSTAL_Y;
                double dx = ec.getX() - CRYSTAL_X;
                double dz = ec.getZ() - CRYSTAL_Z;
                if (Math.abs(dy) < 0.6D && Math.abs(dx) < 0.6D && Math.abs(dz) < 0.6D) {
                    centerCrystalExists = true;
                    TheBeyond.LOGGER.info(
                            "[TheBeyond] Center crystal already exists at ({}, {}, {}) — skipping placement, but force-load and ring kill still ran.",
                            ec.getX(), ec.getY(), ec.getZ());
                    break;
                }
            }
        }

        if (!centerCrystalExists) {
            // Place bedrock + invulnerable end crystal for Stellarity to pick up.
            end.setBlockAndUpdate(BEDROCK_POS, Blocks.BEDROCK.defaultBlockState());

            EndCrystal crystal = EntityType.END_CRYSTAL.create(end);
            if (crystal != null) {
                crystal.setInvulnerable(true);
                crystal.setShowBottom(false);
                crystal.moveTo(CRYSTAL_X, CRYSTAL_Y, CRYSTAL_Z, 0.0F, 0.0F);
                end.addFreshEntity(crystal);
                TheBeyond.LOGGER.info(
                        "[TheBeyond] Placed exit-portal crystal at (0.5, 61, 0.5).");
            } else {
                TheBeyond.LOGGER.warn(
                        "[TheBeyond] Failed to create end crystal entity for Stellarity compat fix.");
            }
        }

        TheBeyond.LOGGER.info(
                "[TheBeyond] Stellarity compat: force-loaded {} chunks for {} ticks, killed {} ring crystals.",
                forcedChunks.size(), UNFORCE_DELAY_TICKS, ringCrystalsKilled);

        tickSincePlacement = 0;
        placedThisSession = true;
    }

    private static void releaseForcedChunks(ServerLevel end) {
        if (forcedChunks.isEmpty()) {
            return;
        }
        for (ChunkPos cp : forcedChunks) {
            end.setChunkForced(cp.x, cp.z, false);
        }
        TheBeyond.LOGGER.info(
                "[TheBeyond] Released {} forced chunks after Stellarity post_gen window closed.",
                forcedChunks.size());
        forcedChunks.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        placedThisSession = false;
        tickSincePlacement = -1;
        // Do not call setChunkForced(false) here — the level is being torn down, tickets are
        // discarded automatically. Just clear our tracking list so a fresh session starts empty.
        forcedChunks.clear();
    }
}
