package com.thebeyond.common.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bulb rules on synthetic spheres. */
class OrganicLifeTest {
    private static final long T0 = 1_000;

    private static long[] sphere(int cx, int cy, int cz, double r) {
        List<Long> out = new ArrayList<>();
        int R = (int) Math.ceil(r);
        for (int a = -R; a <= R; a++) {
            for (int b = -R; b <= R; b++) {
                for (int c = -R; c <= R; c++) {
                    if (a * a + b * b + c * c <= r * r + 0.25) out.add(BlockPos.asLong(cx + a, cy + b, cz + c));
                }
            }
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** A nucleus of radius 3 at the origin and two subs of radius 2 with flows 0.4 and 0.1. */
    private static OrganicLife.Organism organism(String setup) {
        OrganicLife.Organ nucleus = new OrganicLife.Organ(true, 0.5, 0.5, 0.5, sphere(0, 0, 0, 3), 1);
        OrganicLife.Organ a = new OrganicLife.Organ(false, 20.5, 0.5, 0.5, sphere(20, 0, 0, 2), 0.4);
        OrganicLife.Organ b = new OrganicLife.Organ(false, -20.5, 0.5, 0.5, sphere(-21, 0, 0, 2), 0.1);
        return new OrganicLife.Organism(77, setup, new OrganicLife.Organ[]{nucleus, a, b});
    }

    /** Breaks the bulb's blocks from the outside in, skipping its core, until n are broken. */
    private static int breakOutside(OrganicLife.Organism org, int o, int n, long now) {
        OrganicLife.Organ g = org.organ(o);
        int done = 0;
        for (int i = g.size() - 1; i >= 0 && done < n; i--) {
            if (i == g.core || g.state[i] == OrganicLife.BROKEN) continue;
            OrganicLife.Hit h = org.hit(o, g.pos[i], now);
            assertTrue(h.taken(), "break " + i + " refused");
            done++;
            if (h.organDied()) break;
        }
        return done;
    }

    private static int killSub(OrganicLife.Organism org, int o, long now) {
        int n = 0;
        while (!org.organ(o).dead()) {
            n += breakOutside(org, o, 1, now);
        }
        return n;
    }

    @Test
    void closedBulbsCannotBeBrokenAndLightExposesThem() {
        OrganicLife.Organism org = organism("root");
        long p = org.organ(1).pos[org.organ(1).size() - 1];
        assertFalse(org.breakable(1, T0), "a closed sub");
        assertFalse(org.hit(1, p, T0).taken(), "a closed sub refuses the break");
        org.setFlame(1, T0 + 100, T0);
        assertTrue(org.breakable(1, T0 + 99), "the flame exposes it");
        assertFalse(org.breakable(1, T0 + 100), "it closes when the flame goes out");
        org.setBonfire(2, true, T0);
        assertTrue(org.breakable(2, T0 + 1_000_000), "a lit bonfire keeps it exposed");
        org.setBonfire(2, false, T0 + 1_000_000);
        assertFalse(org.breakable(2, T0 + 1_000_000), "closed once the bonfire goes out");
    }

    @Test
    void aSubDiesBelowHalfAndItsCoreKillsItAtOnce() {
        OrganicLife.Organism org = organism("root");
        OrganicLife.Organ g = org.organ(1);
        org.setBonfire(1, true, T0);
        int total = g.size(), broken = killSub(org, 1, T0);
        assertTrue(g.alive() < OrganicLife.DEATH * total, "dead below the limit");
        assertTrue(g.alive() + 1 >= OrganicLife.DEATH * total, "and not before it: " + g.alive() + " of " + total);
        assertEquals(total - g.alive(), broken);
        OrganicLife.Organ h = org.organ(2);
        org.setBonfire(2, true, T0);
        assertTrue(h.core >= 0, "the centre block is part of the bulb");
        OrganicLife.Hit hit = org.hit(2, h.pos[h.core], T0);
        assertEquals(OrganicLife.CORE_KILL, hit.organDied(), "breaking the core kills the sub");
        assertEquals(h.size() - 1, h.alive());
    }

    @Test
    void theNucleusWaitsForItsSubsAndDiesOnlyWhole() {
        OrganicLife.Organism org = organism("slime_mold");
        org.death = OrganicLife.Death.SUBS_FIRST;
        for (int o = 0; o < 3; o++) org.setBonfire(o, true, T0);
        assertFalse(org.breakable(0, T0), "subs still alive");
        killSub(org, 1, T0);
        assertFalse(org.breakable(0, T0), "one sub still alive");
        killSub(org, 2, T0);
        assertTrue(org.subsDead());
        assertTrue(org.breakable(0, T0));
        OrganicLife.Organ n = org.organ(0);
        for (int i = n.size() - 1; i > 0; i--) assertFalse(org.hit(0, n.pos[i], T0).organDied(), "alive with blocks left");
        assertTrue(org.alive());
        OrganicLife.Hit last = org.hit(0, n.pos[0], T0);
        assertTrue(last.organDied() && last.organismDied(), "the last block kills the organism");
        assertFalse(org.alive());
    }

    @Test
    void theNucleusDiesWithItsCentreBlock() {
        OrganicLife.Organism org = organism("slime_mold");
        org.death = OrganicLife.Death.SPLIT;
        org.shield = 1;
        for (int o = 0; o < 3; o++) org.setBonfire(o, true, T0);
        OrganicLife.Organ n = org.organ(0);
        assertTrue(n.core >= 0 && org.core(0, n.pos[n.core]), "the centre block is the nucleus's core");
        assertFalse(org.core(0, n.pos[n.core == 0 ? 1 : 0]), "only the centre is the core");
        OrganicLife.Hit h = org.hit(0, n.pos[n.core], T0);
        assertTrue(h.organDied() && h.organismDied(), "the centre kills the nucleus with its other blocks left");
        assertTrue(n.size() > 1);
    }

    @Test
    void cascadeAndSplitLetTheNucleusBreakWhileItsSubsLive() {
        assertEquals(OrganicLife.Death.CASCADE, OrganicLife.death("root"));
        assertEquals(OrganicLife.Death.SPLIT, OrganicLife.death("slime_mold"));
        assertEquals(OrganicLife.Death.SUBS_FIRST, OrganicLife.death(""));
        for (OrganicLife.Death d : new OrganicLife.Death[]{OrganicLife.Death.CASCADE, OrganicLife.Death.SPLIT}) {
            OrganicLife.Organism org = organism("slime_mold");
            org.death = d;
            org.shield = 1;
            for (int o = 0; o < 3; o++) org.setBonfire(o, true, T0);
            assertTrue(org.breakable(0, T0), d + ": the nucleus breaks with its subs alive");
            OrganicLife.Organ n = org.organ(0);
            OrganicLife.Hit last = null;
            for (int i = n.size() - 1; i >= 0; i--) last = org.hit(0, n.pos[i], T0);
            assertTrue(last.organDied() && last.organismDied(), d + ": the last block kills the nucleus");
            assertTrue(org.alive(), d + ": the subs outlive it until the world's rule decides");
            org.kill(1);
            org.kill(2);
            assertFalse(org.alive(), d + ": nothing left alive");
        }
    }

    @Test
    void theSubsShieldTheirNucleusUntilTheirLiveFlowFallsToTheShield() {
        assertEquals(0.5, OrganicLife.shield("slime_mold"), 0);
        OrganicLife.Organ nucleus = new OrganicLife.Organ(true, 0.5, 0.5, 0.5, sphere(0, 0, 0, 3), 1);
        OrganicLife.Organ a = new OrganicLife.Organ(false, 20.5, 0.5, 0.5, sphere(20, 0, 0, 2), 0.5);
        OrganicLife.Organ b = new OrganicLife.Organ(false, -19.5, 0.5, 0.5, sphere(-20, 0, 0, 2), 0.3);
        OrganicLife.Organ c = new OrganicLife.Organ(false, 0.5, 0.5, 20.5, sphere(0, 0, 20, 2), 0.2);
        OrganicLife.Organism org = new OrganicLife.Organism(79, "slime_mold", new OrganicLife.Organ[]{nucleus, a, b, c},
                new boolean[]{true, false, false, false}, new double[]{1, 0.5, 0.3, 0.2});
        org.setBonfire(0, true, T0);
        assertTrue(org.exposed(0, T0), "VOID: the bonfire exposes the nucleus");
        assertFalse(org.breakable(0, T0), "a lit nucleus fed by all its subs stays shut");
        org.kill(2);
        assertEquals(0.7, org.fed(), 1e-9);
        assertFalse(org.breakable(0, T0), "losing 30% of the flow leaves it shielded");
        org.kill(1);
        assertEquals(0.2, org.fed(), 1e-9);
        assertTrue(org.breakable(0, T0), "at 20% the shield is down");
        org.shield = 0;
        assertFalse(org.breakable(0, T0), "shield 0 waits for every sub");
        org.death = OrganicLife.Death.SUBS_FIRST;
        org.shield = 1;
        assertFalse(org.breakable(0, T0), "SUBS_FIRST waits for every sub whatever the shield");
        float[] table = new float[4 * OrganicNetwork.ORGAN];
        float[] flows = {1, 0.5f, 0.3f, 0.2f};
        for (int o = 0; o < 4; o++) {
            table[o * OrganicNetwork.ORGAN + 4] = o == 0 ? 0 : 1;
            table[o * OrganicNetwork.ORGAN + 6] = flows[o];
        }
        assertTrue(OrganicLife.bornShielded("slime_mold", table, null), "born whole, born shut");
        assertFalse(OrganicLife.bornShielded("slime_mold", table, java.util.Set.of(1, 2)), "born after its feeders died, open");
    }

    @Test
    void anUnseenSubKilledByItsNucleusStaysDeadAfterASaveAndOnceSeen() {
        OrganicLife.Organ nucleus = new OrganicLife.Organ(true, 0.5, 0.5, 0.5, sphere(0, 0, 0, 3), 1);
        OrganicLife.Organism org = new OrganicLife.Organism(78, "root", new OrganicLife.Organ[]{nucleus, null},
                new boolean[]{true, false}, new double[]{1, 0.5});
        org.kill(1);
        assertTrue(org.dead(1));
        OrganicLife.Organism back = OrganicLife.Organism.load(org.save());
        assertTrue(back.dead(1), "the kill survives a save");
        back.fill(1, new OrganicLife.Organ(false, 20.5, 0.5, 0.5, sphere(20, 0, 0, 2), 0.5));
        assertTrue(back.organ(1).dead(), "the organ seen later is dead");
    }

    @Test
    void anExposedBulbNeverHealsAndHealsOnlyAfterThePauseInTheDark() {
        OrganicLife.Organism org = organism("root");
        org.setBonfire(1, true, T0);
        breakOutside(org, 1, 5, T0);
        OrganicLife.Organ g = org.organ(1);
        int alive = g.alive();
        assertTrue(org.advance(T0 + 2_000_000, p -> true).isEmpty(), "no healing under the bonfire's light");
        long out = T0 + 2_000_000;
        org.setBonfire(1, false, out);
        assertTrue(org.advance(out + OrganicLife.PAUSE - 1, p -> true).isEmpty(), "still in the pause");
        List<OrganicLife.Growth> grown = org.advance(out + OrganicLife.PAUSE + 20 * 60, p -> true);
        assertFalse(grown.isEmpty(), "heals after the pause");
        assertEquals(alive + grown.size(), g.alive());
        long flame = out + 100_000;
        breakOutsideExposed(org, 1, 3, flame);
        int before = g.alive();
        assertTrue(org.advance(flame + 600, p -> true).isEmpty(), "the flame keeps it open");
        assertTrue(org.advance(flame + 1200 + OrganicLife.PAUSE - 1, p -> true).isEmpty(), "pause after the flame");
        assertFalse(org.advance(flame + 1200 + OrganicLife.PAUSE + 20 * 30, p -> true).isEmpty(), "then it heals");
        assertTrue(g.alive() > before);
    }

    private static void breakOutsideExposed(OrganicLife.Organism org, int o, int n, long now) {
        org.setFlame(o, now + 1200, now);
        breakOutside(org, o, n, now);
    }

    @Test
    void lightStopsHealingOnlyWhereTheNetworkFleesIt() {
        for (String setup : new String[]{"slime_mold", "root"}) {
            OrganicLife.Organism org = organism(setup);
            org.setFlame(1, T0 + 10, T0);
            breakOutside(org, 1, 5, T0);
            org.setLight(1, true, T0);
            List<OrganicLife.Growth> g = org.advance(T0 + 20 * 600, p -> true);
            assertEquals(OrganicLight.shy(setup), g.isEmpty(), setup + ": a torch stops the healing only when shy");
            assertFalse(org.breakable(1, T0 + 20), setup + ": a torch alone does not expose");
        }
    }

    @Test
    void regrowthTouchesLiveTissueGoesFromTheCoreOutAndGivesNoMaterial() {
        OrganicLife.Organism org = organism("slime_mold");
        OrganicLife.Organ g = org.organ(1);
        org.setFlame(1, T0 + 10, T0);
        breakOutside(org, 1, 7, T0);
        Set<Long> broken = new HashSet<>();
        for (int i = 0; i < g.size(); i++) if (g.state[i] == OrganicLife.BROKEN) broken.add(g.pos[i]);
        long t = T0 + 10 + OrganicLife.PAUSE;
        double lastDistance = -1;
        for (int step = 0; step < 7; step++) {
            List<OrganicLife.Growth> grown = new ArrayList<>();
            while (grown.isEmpty()) grown = org.advance(t += 5, p -> true);
            assertEquals(1, grown.size(), "one block per event at this speed");
            long p = grown.get(0).pos();
            assertTrue(broken.remove(p), "only broken blocks grow back");
            boolean touches = false;
            for (Direction d : Direction.values()) touches |= g.state(BlockPos.offset(p, d)) != OrganicLife.BROKEN
                    && g.state(BlockPos.offset(p, d)) >= 0;
            assertTrue(touches, "grows against live tissue");
            double d = BlockPos.of(p).distToCenterSqr(20.5, 0.5, 0.5);
            assertTrue(d >= lastDistance - 1e-9, "from the core out");
            lastDistance = d;
            for (long q : broken) {
                if (BlockPos.of(q).distToCenterSqr(20.5, 0.5, 0.5) < d - 1e-9) {
                    boolean live = false;
                    for (Direction e : Direction.values()) {
                        byte s = g.state(BlockPos.offset(q, e));
                        live |= s >= 0 && s != OrganicLife.BROKEN;
                    }
                    assertFalse(live, "a nearer broken block touching life was skipped");
                }
            }
        }
        assertEquals(g.size(), g.alive(), "healed whole");
        long regrown = -1;
        for (int i = 0; i < g.size(); i++) if (g.state[i] == OrganicLife.REGROWN) regrown = g.pos[i];
        org.setFlame(1, t + 100, t);
        assertFalse(org.hit(1, regrown, t).material(), "regrown tissue drops nothing");
        long original = -1;
        for (int i = 0; i < g.size(); i++) if (g.state[i] == OrganicLife.ALIVE && i != g.core) original = g.pos[i];
        assertTrue(org.hit(1, original, t).material(), "original tissue drops the material");
    }

    /** Ticks from the end of the pause until the sub is whole again, after breaking n of its blocks. */
    private static double healTime(String setup, int o, int n) {
        OrganicLife.Organism org = organism(setup);
        org.setFlame(o, T0 + 1, T0);
        breakOutside(org, o, n, T0);
        long start = T0 + 1 + OrganicLife.PAUSE;
        OrganicLife.Organ g = org.organ(o);
        long t = start;
        while (g.alive() < g.size()) {
            org.advance(t, p -> true);
            t += 1;
            assertTrue(t - start < 20L * 60 * 60 * 10, "heals within ten hours");
        }
        return t - start;
    }

    @Test
    void speedFollowsTheSetupTheHealthAndTheFlow() {
        int n = 10;
        double slime_mold = healTime("slime_mold", 1, n), fungus = healTime("fungus", 1, n), root = healTime("root", 1, n);
        assertTrue(slime_mold < fungus && fungus < root, "slime_mold faster than fungus, fungus faster than root: " + slime_mold + " " + fungus + " " + root);
        assertEquals(OrganicLife.speed("slime_mold") / OrganicLife.speed("root"), root / slime_mold, 0.02 * root / slime_mold, "time scales with speed");
        double rich = healTime("fungus", 1, n), poor = healTime("fungus", 2, n);
        assertTrue(rich < poor, "the better fed sub heals faster: " + rich + " vs " + poor);
        OrganicLife.Organism a = organism("fungus"), b = organism("fungus");
        a.setFlame(1, T0 + 1, T0);
        b.setFlame(1, T0 + 1, T0);
        breakOutside(a, 1, 2, T0);
        breakOutside(b, 1, 14, T0);
        double fast = a.organ(1).rate(OrganicLife.speed("fungus"), 1), slow = b.organ(1).rate(OrganicLife.speed("fungus"), 1);
        assertTrue(fast > 2 * slow, "a sub at " + a.organ(1).alive() + " heals faster than at " + b.organ(1).alive());
    }

    @Test
    void aNucleusWithoutSubsHealsSlowlyFromItsReserveOnly() {
        OrganicLife.Organism org = organism("slime_mold");
        for (int o = 0; o < 3; o++) org.setBonfire(o, true, T0);
        killSub(org, 1, T0);
        killSub(org, 2, T0);
        OrganicLife.Organ n = org.organ(0);
        int size = n.size(), lost = 0;
        for (int i = size - 1; i > size / 2; i--) {
            org.hit(0, n.pos[i], T0);
            lost++;
        }
        for (int o = 0; o < 3; o++) org.setBonfire(o, false, T0);
        int grown = org.advance(T0 + 20L * 3600 * 24, p -> true).size();
        assertEquals(lost, grown, "whole again inside a day");
        assertEquals(OrganicLife.RESERVE * size - lost, n.reserve, 1e-9, "spent from the reserve");
        OrganicLife.Organ sub = new OrganicLife.Organ(false, 0.5, 0.5, 0.5, sphere(0, 0, 0, 3), 1);
        assertTrue(n.rate(1, 1) < sub.rate(1, 1), "slower than a fed sub of the same size");
        org.setBonfire(0, true, T0 + 1);
        for (int round = 0; round < 3; round++) {
            for (int i = size - 1; i > 0; i--) org.hit(0, n.pos[i], T0 + 2);
            org.setBonfire(0, false, T0 + 2);
            org.advance(T0 + 20L * 3600 * 24 * (round + 2), p -> true);
            org.setBonfire(0, true, T0 + 20L * 3600 * 24 * (round + 2));
        }
        assertTrue(n.reserve < 1, "the reserve runs out: " + n.reserve);
    }

    @Test
    void healingDoesNotDependOnHowTimeIsSplit() {
        Random r = new Random(3);
        OrganicLife.Organism one = organism("fungus"), many = organism("fungus");
        for (OrganicLife.Organism org : new OrganicLife.Organism[]{one, many}) {
            org.setFlame(1, T0 + 1, T0);
            org.setFlame(2, T0 + 1, T0);
            breakOutside(org, 1, 9, T0);
            breakOutside(org, 2, 6, T0);
        }
        long end = T0 + 20 * 400;
        List<OrganicLife.Growth> a = one.advance(end, p -> true), b = new ArrayList<>();
        for (long t = T0; t < end; t += 1 + r.nextInt(97)) b.addAll(many.advance(t, p -> true));
        b.addAll(many.advance(end, p -> true));
        assertFalse(a.isEmpty());
        // a split run interleaves the bulbs, each bulb's own sequence must match
        for (int o = 0; o < one.size(); o++) {
            final int k = o;
            assertEquals(a.stream().filter(x -> x.organ() == k).toList(), b.stream().filter(x -> x.organ() == k).toList(),
                    "bulb " + o + ": same blocks in the same order");
        }
        assertEquals(one.save(), many.save(), "same state");
    }

    @Test
    void organismsSurviveSaveAndLoad() {
        OrganicLife.Organism org = organism("slime_mold");
        org.setFlame(1, T0 + 1, T0);
        breakOutside(org, 1, 6, T0);
        org.setBonfire(2, true, T0);
        org.setLight(0, true, T0);
        org.advance(T0 + OrganicLife.PAUSE + 50, p -> true);
        CompoundTag tag = org.save();
        OrganicLife.Organism back = OrganicLife.Organism.load(tag);
        assertEquals(tag, back.save(), "the same tag after a round trip");
        assertEquals(org.advance(T0 + 20 * 300, p -> true), back.advance(T0 + 20 * 300, p -> true), "and the same future");
        OrganicLife.Data data = new OrganicLife.Data();
        data.put(org);
        data.put(new OrganicLife.Organism(78, "root", organism("root").organs));
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved = data.save(new CompoundTag(), provider);
        OrganicLife.Data loaded = OrganicLife.Data.load(saved, provider);
        assertEquals(saved, loaded.save(new CompoundTag(), provider), "saved data round trip");
        assertEquals(org.describe(), loaded.get(77).describe());
    }

    @Test
    void unseenOrgansCountAsAliveAndClosedUntilFilledIn() {
        OrganicLife.Organ nucleus = new OrganicLife.Organ(true, 0.5, 0.5, 0.5, sphere(0, 0, 0, 3), 1);
        OrganicLife.Organ a = new OrganicLife.Organ(false, 20.5, 0.5, 0.5, sphere(20, 0, 0, 2), 0.4);
        OrganicLife.Organism org = new OrganicLife.Organism(78, "slime_mold", new OrganicLife.Organ[]{nucleus, a, null},
                new boolean[]{true, false, false}, new double[]{1, 0.4, 0.1});
        org.death = OrganicLife.Death.SUBS_FIRST;
        for (int o = 0; o < 3; o++) org.setBonfire(o, true, T0);
        killSub(org, 1, T0);
        assertFalse(org.subsDead(), "an unseen sub counts as alive");
        assertFalse(org.breakable(0, T0), "so the nucleus stays closed to breaking");
        assertFalse(org.exposed(2, T0));
        assertFalse(org.hit(2, BlockPos.asLong(-21, 0, 0), T0, true).taken(), "nothing to hit before it is seen");
        CompoundTag tag = org.save();
        OrganicLife.Organism back = OrganicLife.Organism.load(tag);
        assertFalse(back.seen(2));
        assertTrue(back.seen(1));
        assertEquals(tag, back.save(), "the same tag again");
        back.fill(2, new OrganicLife.Organ(false, -20.5, 0.5, 0.5, sphere(-21, 0, 0, 2), 0.1));
        back.setBonfire(2, true, T0);
        killSub(back, 2, T0);
        assertTrue(back.subsDead());
        assertTrue(back.breakable(0, T0), "with every sub seen and dead the nucleus can be broken");
    }

    @Test
    void forceTakesAClosedBlock() {
        OrganicLife.Organism org = organism("slime_mold");
        OrganicLife.Organ g = org.organ(1);
        long p = g.pos[g.size() - 1];
        assertFalse(org.hit(1, p, T0).taken(), "closed, a hand cannot break it");
        OrganicLife.Hit h = org.hit(1, p, T0, true);
        assertTrue(h.taken());
        assertTrue(h.material(), "original tissue");
        assertFalse(org.hit(1, p, T0, true).taken(), "already broken");
    }
}
