package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** One dimension's organic work in progress, kept together so a world reset drops all of it at once. */
final class OrganicLevelState {
    private static final Map<ResourceKey<Level>, OrganicLevelState> STATES = new HashMap<>();

    final List<OrganicFire.Cleaning> cleanings = new ArrayList<>();
    final Set<Long> pending = new HashSet<>();
    // bulbs a live flame keeps exposed, packed as cell and organ, to close them once it burns out
    final Set<long[]> flames = new HashSet<>();
    final List<OrganicDeath.Withering> witherings = new ArrayList<>();
    final Set<Long> atrophyDue = new HashSet<>();
    final List<OrganicReturn.Return> returns = new ArrayList<>();
    final Set<Long> returning = new HashSet<>();
    final Set<Long> releasing = new HashSet<>();
    final Set<Long> trailsDue = new HashSet<>();
    final Set<Long> biomeDirty = new HashSet<>();
    long biomeSent = Long.MIN_VALUE;
    int biomeCalls, biomeSends, biomeChunks;
    // the trace logs onTick's time per tick every 200 ticks when any of them had work, with the slowest tick of each part
    private static final String[] PARTS = {"fronts", "witherings", "pending", "returns", "returning", "heal", "releasing",
            "atrophy", "flush"};
    private final long[] tickNs = new long[200], partMax = new long[PARTS.length];
    private int ticks, busy;

    private OrganicLevelState() {}

    boolean working() {
        return !cleanings.isEmpty() || !witherings.isEmpty() || !returns.isEmpty() || !pending.isEmpty() || !releasing.isEmpty()
                || !atrophyDue.isEmpty() || !biomeDirty.isEmpty();
    }

    long lap(int part, long since) {
        long t = System.nanoTime();
        partMax[part] = Math.max(partMax[part], t - since);
        return t;
    }

    void timed(ServerLevel level, long ns, boolean work) {
        tickNs[ticks++] = ns;
        if (work) busy++;
        if (ticks < tickNs.length) return;
        if (busy > 0) {
            long[] s = tickNs.clone();
            Arrays.sort(s);
            long sum = 0;
            for (long v : s) sum += v;
            StringBuilder parts = new StringBuilder();
            for (int i = 0; i < PARTS.length; i++) {
                if (partMax[i] >= 100_000) parts.append(String.format(Locale.ROOT, " %s %.1f", PARTS[i], partMax[i] / 1e6));
            }
            TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG tick %s | ticks %d busy %d | ms p50 %.3f p90 %.3f max %.3f"
                            + " sum %.1f | fronts %d witherings %d returns %d | biome calls %d sends %d chunks %d | slowest ms%s",
                    level.dimension().location(), ticks, busy, s[ticks / 2] / 1e6, s[ticks * 9 / 10] / 1e6, s[ticks - 1] / 1e6,
                    sum / 1e6, cleanings.size(), witherings.size(), returns.size(), biomeCalls, biomeSends, biomeChunks, parts));
        }
        Arrays.fill(partMax, 0);
        ticks = busy = 0;
        biomeCalls = biomeSends = biomeChunks = 0;
    }

    static OrganicLevelState of(ServerLevel level) {
        return STATES.computeIfAbsent(level.dimension(), k -> new OrganicLevelState());
    }

    static void forget() {
        STATES.clear();
    }
}
