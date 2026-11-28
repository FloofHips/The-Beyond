package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.OrganicBulbBlock;
import com.thebeyond.common.block.OrganicTissueBlock;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** A root or hypha the player breaks is cleared and grows back from the live tissue beside it after the bulbs' pause. */
final class OrganicRegrow {
    // "off" leaves a broken root as air for good
    static final boolean ON = !"off".equals(System.getProperty("beyond.sub.org.regrow", "on"));

    private OrganicRegrow() {}

    static void broken(ServerLevel level, BlockPos pos, BlockState state) {
        if (!ON) return;
        LevelChunk ch = level.getChunkAt(pos);
        OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
        if (h == null) return;
        int i = h.find(OrganicTissue.pack(pos.getX(), pos.getY(), pos.getZ(), ch.getMinBuildHeight()));
        if (i < 0 || h.gone(i) || OrganicWorld.dead(h.cell(i), h.organ(i))) return;
        h.clear(i, state);
        ch.setUnsaved(true);
        OrganicLife.Data d = OrganicLife.Data.get(level);
        d.broken.put(pos.asLong(), level.getGameTime() + OrganicLife.PAUSE);
        d.setDirty();
        if (OrganicWorld.TRACE) {
            TheBeyond.LOGGER.info("ORG broken {} | cell {} organ {} | back after {} ticks", pos.toShortString(), h.cell(i),
                    h.organ(i), OrganicLife.PAUSE);
        }
    }

    /** Due breaks beside live tissue grow back, so the front walks in from the cut ends one ring per heal. */
    static void advance(ServerLevel level, long now) {
        OrganicLife.Data d = OrganicLife.Data.get(level);
        if (d.broken.isEmpty()) return;
        List<BlockPos> ready = new ArrayList<>();
        for (Iterator<Map.Entry<Long, Long>> it = d.broken.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> e = it.next();
            if (e.getValue() > now) continue;
            BlockPos p = BlockPos.of(e.getKey());
            if (!level.isLoaded(p)) continue;
            LevelChunk ch = level.getChunkAt(p);
            OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
            int i = h == null ? -1 : h.find(OrganicTissue.pack(p.getX(), p.getY(), p.getZ(), ch.getMinBuildHeight()));
            // a fire's return grew it back, or its organ died and withers: nothing left to regrow here
            if (i < 0 || !h.cleared(i) || OrganicWorld.dead(h.cell(i), h.organ(i))) {
                it.remove();
                d.setDirty();
                continue;
            }
            if (!level.getBlockState(p).isAir() || OrganicReturn.touchesBlockEntity(level, p) || !touchesLive(level, p)) continue;
            OrganicLife.Organism org = d.get(h.cell(i));
            if (org != null && org.shy && OrganicWorld.lit(level, p)) continue;
            ready.add(p);
        }
        int grown = 0;
        for (BlockPos p : ready) {
            LevelChunk ch = level.getChunkAt(p);
            OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
            BlockState s = h.regrow(h.find(OrganicTissue.pack(p.getX(), p.getY(), p.getZ(), ch.getMinBuildHeight())));
            d.broken.remove(p.asLong());
            d.setDirty();
            ch.setUnsaved(true);
            if (s == null) continue;
            OrganicWorld.set(level, p, s);
            grown++;
        }
        if (grown > 0 && OrganicWorld.TRACE) {
            TheBeyond.LOGGER.info("ORG regrow broken | blocks {} | still waiting {}", grown, d.broken.size());
        }
    }

    private static boolean touchesLive(ServerLevel level, BlockPos p) {
        for (Direction dir : OrganicWorld.DIRS) {
            BlockPos n = p.relative(dir);
            if (!level.isLoaded(n)) continue;
            BlockState s = level.getBlockState(n);
            if (s.getBlock() instanceof OrganicTissueBlock || s.getBlock() instanceof OrganicBulbBlock) return true;
        }
        return false;
    }
}
