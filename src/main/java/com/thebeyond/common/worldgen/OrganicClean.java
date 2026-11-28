package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The order in which a hand-lit bonfire's light clears the light-shy network in its clearing. */
public final class OrganicClean {
    public static final int TICKS = Math.max(1, Integer.getInteger("beyond.sub.org.clean.ticks", 100));
    public static final boolean REGROW = Boolean.getBoolean("beyond.sub.org.regrow");
    static final double FRONT = 0.75;
    static final double WOBBLE = 0.08;
    static final int DEPTH = 3;
    private static final Direction[] DIRS = Direction.values();

    private OrganicClean() {}

    public record Step(long pos, int tick, OrganicLight.Action action, byte kind) {}

    public record Exposure(int owner, int tick) {}

    public record Plan(List<Step> steps, List<Exposure> exposures, int maxPerTick) {}

    /** The steps for the network blocks inside one clearing, given by position, kind and owner id (-1 for none). */
    public static Plan plan(OrganicLight.Bonfire fire, long[] pos, byte[] kind, int[] owner, long seed) {
        int n = pos.length;
        Map<Long, Integer> at = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) at.put(pos[i], i);
        int[] depth = new int[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            depth[i] = -1;
            for (Direction d : DIRS) {
                if (!at.containsKey(BlockPos.offset(pos[i], d))) {
                    depth[i] = 0;
                    queue.add(i);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            for (Direction d : DIRS) {
                Integer j = at.get(BlockPos.offset(pos[i], d));
                if (j == null || depth[j] >= 0) continue;
                depth[j] = depth[i] + 1;
                queue.add(j);
            }
        }
        SimplexNoise noise = new SimplexNoise(new XoroshiroRandomSource(seed ^ 0x636C65616EL));
        List<Step> steps = new ArrayList<>();
        Map<Integer, Integer> exposed = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            OrganicLight.Action a = OrganicLight.action(kind[i]);
            if (a == OrganicLight.Action.KEEP) continue;
            double x = BlockPos.getX(pos[i]) + 0.5, y = BlockPos.getY(pos[i]) + 0.5, z = BlockPos.getZ(pos[i]) + 0.5;
            double s = Math.min(1, fire.ratio(x, y, z)), inner = Math.min(1, Math.max(0, depth[i]) / (double) DEPTH);
            double u = FRONT * s + (1 - FRONT) * inner + WOBBLE * noise.getValue(x / 6, y / 6, z / 6);
            int tick = (int) Math.round(TICKS * Math.max(0, Math.min(1, u)));
            if (a == OrganicLight.Action.BREAKABLE) {
                if (owner[i] >= 0) exposed.merge(owner[i], tick, Math::min);
            } else {
                steps.add(new Step(pos[i], tick, a, kind[i]));
            }
        }
        steps.sort(Comparator.comparingInt(Step::tick).thenComparingLong(Step::pos));
        int top = 0;
        for (int i = 0, run = 0; i < steps.size(); i++) {
            run = i > 0 && steps.get(i - 1).tick() == steps.get(i).tick() ? run + 1 : 1;
            top = Math.max(top, run);
        }
        List<Exposure> ex = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : exposed.entrySet()) ex.add(new Exposure(e.getKey(), e.getValue()));
        return new Plan(steps, ex, top);
    }

    /** A hand-lit bonfire went out: with regrow on, the network it cleared would grow back from here. */
    public static void onBonfireOut(ServerLevel level, BlockPos fire) {
        if (REGROW && OrganicExperiment.ON) TheBeyond.LOGGER.info("ORG regrow hook: bonfire out at {}", fire.toShortString());
    }
}
