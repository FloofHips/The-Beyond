package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongPredicate;

/** An organism's bulbs over game time, the light that exposes them, breaking, dying and healing. */
public final class OrganicLife {
    public static final byte ALIVE = 0, REGROWN = 1, BROKEN = 2;
    public static final double DEATH = Double.parseDouble(System.getProperty("beyond.sub.org.bulb.death", "0.5"));
    public static final boolean CORE_KILL = Boolean.parseBoolean(System.getProperty("beyond.sub.org.bulb.core", "true"));
    public static final boolean NUCLEUS_CORE = Boolean.parseBoolean(System.getProperty("beyond.sub.org.nucleus.core", "true"));
    public static final long PAUSE = Math.round(20 * Double.parseDouble(System.getProperty("beyond.sub.org.bulb.pause", "10")));
    static final double HEAL_MIN = Double.parseDouble(System.getProperty("beyond.sub.org.bulb.healMin", "0.1"));
    static final double NUCLEUS_SUPPLY = Double.parseDouble(System.getProperty("beyond.sub.org.bulb.nucleus", "0.25"));
    static final double RESERVE = Double.parseDouble(System.getProperty("beyond.sub.org.bulb.reserve", "1"));
    // blocks per second with full health and full supply
    private static final Map<String, Double> SPEED = numbers(System.getProperty("beyond.sub.org.bulb.speed",
            "slime_mold=1,fungus=0.5,root=0.2,strangler_fig=0.2"), "bulb speed");
    private static final double SPEED_OTHER = 0.5;

    /** A nucleus's death: SUBS_FIRST waits for every sub, CASCADE takes the subs not grafted elsewhere, SPLIT spares them. */
    public enum Death { SUBS_FIRST, CASCADE, SPLIT }

    private static final Map<String, Death> DEATHS = deaths(System.getProperty("beyond.sub.org.death",
            "root=cascade,strangler_fig=cascade,fungus=split,slime_mold=split"));
    private static final Map<String, Double> SHIELDS = numbers(System.getProperty("beyond.sub.org.shield",
            "root=0.5,strangler_fig=0.5,fungus=0.5,slime_mold=0.5"), "nucleus shield");
    private static final double SHIELD_OTHER = 0.5;
    private static final Direction[] DIRS = Direction.values();

    private OrganicLife() {}

    public static double speed(String setup) {
        return OrganicPresets.number(setup, "bulb.speed", SPEED, SPEED_OTHER);
    }

    public static Death death(String setup) {
        String v = setup == null ? null : OrganicPresets.value(setup, "bulb.death");
        if (v != null) {
            try {
                return Death.valueOf(v.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                TheBeyond.LOGGER.error("[Beyond] organic preset {}: bulb.death {} is not subs_first, cascade or split", setup, v);
            }
        }
        Death d = setup == null ? null : DEATHS.get(setup);
        if (d == null && setup != null) d = DEATHS.get(OrganicPresets.base(setup));
        return d == null ? Death.SUBS_FIRST : d;
    }

    /** Live share of the subs' flow above which they keep their nucleus shut: 0 waits for every sub, 1 never shields. */
    public static double shield(String setup) {
        return OrganicPresets.number(setup, "bulb.shield", SHIELDS, SHIELD_OTHER);
    }

    static double bornFed(float[] table, Set<Integer> dead) {
        double all = 0, live = 0;
        for (int o = 0; (o + 1) * OrganicNetwork.ORGAN <= table.length; o++) {
            if (table[o * OrganicNetwork.ORGAN + 4] == 0) continue;
            double f = table[o * OrganicNetwork.ORGAN + 6], w = Double.isFinite(f) && f > 0 ? f : 1;
            all += w;
            if (dead == null || !dead.contains(o)) live += w;
        }
        return all > 0 ? live / all : 0;
    }

    static boolean bornShielded(String setup, float[] table, Set<Integer> dead) {
        double fed = bornFed(table, dead);
        return death(setup) == Death.SUBS_FIRST ? fed > 0 : fed > shield(setup) + 1e-9;
    }

    private static Map<String, Death> deaths(String spec) {
        Map<String, Death> out = new HashMap<>();
        for (String kv : spec.split(",")) {
            String[] p = kv.trim().split("=");
            if (p.length != 2) throw new IllegalArgumentException("unknown nucleus death " + kv);
            out.put(p[0], Death.valueOf(p[1].trim().toUpperCase(Locale.ROOT)));
        }
        return Map.copyOf(out);
    }

    private static Map<String, Double> numbers(String spec, String what) {
        Map<String, Double> out = new HashMap<>();
        for (String kv : spec.split(",")) {
            String[] p = kv.trim().split("=");
            if (p.length != 2) throw new IllegalArgumentException("unknown " + what + " " + kv);
            out.put(p[0], Double.parseDouble(p[1]));
        }
        return Map.copyOf(out);
    }

    /** What a break did: whether it was taken, whether it gives material, whether the bulb died and if it was the nucleus. */
    public record Hit(boolean taken, boolean material, boolean organDied, boolean organismDied) {
        static final Hit REFUSED = new Hit(false, false, false, false);
    }

    /** A regrown block: the organ and the block. */
    public record Growth(int organ, long pos) {}

    /** One bulb: its blocks from the centre out, each alive, regrown (it drops nothing) or broken. */
    public static final class Organ {
        public final boolean nucleus;
        final long[] pos;
        final byte[] state;
        final int core;
        final double flow;
        int alive;
        boolean dead, bonfire, light;
        long flameUntil = Long.MIN_VALUE, openEnd = Long.MIN_VALUE, seen = Long.MIN_VALUE;
        double nextAt = Double.NaN, reserve;
        private Map<Long, Integer> index;

        /** Blocks are sorted by distance to the centre (then by position), the order they grow back in. The first is the core. */
        public Organ(boolean nucleus, double cx, double cy, double cz, long[] blocks, double flow) {
            this.nucleus = nucleus;
            this.flow = flow;
            long[] p = blocks.clone();
            double[] d = new double[p.length];
            Integer[] order = new Integer[p.length];
            for (int i = 0; i < p.length; i++) {
                order[i] = i;
                double x = BlockPos.getX(p[i]) + 0.5 - cx, y = BlockPos.getY(p[i]) + 0.5 - cy, z = BlockPos.getZ(p[i]) + 0.5 - cz;
                d[i] = x * x + y * y + z * z;
            }
            Arrays.sort(order, (a, b) -> d[a] != d[b] ? Double.compare(d[a], d[b]) : Long.compare(p[a], p[b]));
            pos = new long[p.length];
            for (int i = 0; i < p.length; i++) pos[i] = p[order[i]];
            state = new byte[p.length];
            alive = p.length;
            core = p.length > 0 ? 0 : -1;
            reserve = RESERVE * p.length;
            dead = p.length == 0;
        }

        private Organ(CompoundTag t) {
            nucleus = t.getBoolean("nucleus");
            // array tags keep the array they were given, so neither side may share it
            pos = t.getLongArray("pos").clone();
            state = t.getByteArray("state").clone();
            core = t.getInt("core");
            flow = t.contains("flow") ? t.getDouble("flow") : Double.NaN;
            dead = t.getBoolean("dead");
            bonfire = t.getBoolean("bonfire");
            light = t.getBoolean("light");
            flameUntil = t.getLong("flame");
            openEnd = t.getLong("openEnd");
            seen = t.getLong("seen");
            nextAt = t.contains("next") ? t.getDouble("next") : Double.NaN;
            reserve = t.getDouble("reserve");
            for (byte s : state) {
                if (s != BROKEN) alive++;
            }
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putBoolean("nucleus", nucleus);
            t.putLongArray("pos", pos.clone());
            t.putByteArray("state", state.clone());
            t.putInt("core", core);
            // NaN is left out, a NaN tag never equals itself
            if (!Double.isNaN(flow)) t.putDouble("flow", flow);
            t.putBoolean("dead", dead);
            t.putBoolean("bonfire", bonfire);
            t.putBoolean("light", light);
            t.putLong("flame", flameUntil);
            t.putLong("openEnd", openEnd);
            t.putLong("seen", seen);
            if (!Double.isNaN(nextAt)) t.putDouble("next", nextAt);
            t.putDouble("reserve", reserve);
            return t;
        }

        public int size() {
            return pos.length;
        }

        public int alive() {
            return alive;
        }

        public boolean dead() {
            return dead;
        }

        public byte state(long p) {
            Integer i = index().get(p);
            return i == null ? -1 : state[i];
        }

        int indexOf(long p) {
            Integer i = index().get(p);
            return i == null ? -1 : i;
        }

        private Map<Long, Integer> index() {
            if (index == null) {
                index = new HashMap<>(pos.length * 2);
                for (int i = 0; i < pos.length; i++) index.put(pos[i], i);
            }
            return index;
        }

        /** Light with no end of its own: a lit bonfire around it, or any strong light at a bulb that flees the light. */
        boolean open(boolean shy) {
            return bonfire || (shy && light);
        }

        /** The tick from which the bulb is dark, MAX_VALUE while an open light lasts. */
        long litUntil(boolean shy) {
            return open(shy) ? Long.MAX_VALUE : Math.max(flameUntil, openEnd);
        }

        boolean exposed(long now) {
            return bonfire || now < flameUntil;
        }

        double rate(double speed, double flowNorm) {
            double a = (double) alive / pos.length;
            double health = nucleus || DEATH >= 1 ? Math.max(HEAL_MIN, a) : Math.max(HEAL_MIN, (a - DEATH) / (1 - DEATH));
            double supply = nucleus ? NUCLEUS_SUPPLY : 0.5 + 0.5 * flowNorm;
            return speed / 20 * health * supply;
        }

        boolean full() {
            return alive == pos.length || (nucleus && reserve < 1);
        }

        /** The broken block nearest the centre that touches live tissue of this bulb and has room to grow. */
        int candidate(LongPredicate canGrow) {
            for (int i = 0; i < pos.length; i++) {
                if (state[i] != BROKEN || !touchesLife(i)) continue;
                if (canGrow.test(pos[i])) return i;
            }
            return -1;
        }

        private boolean touchesLife(int i) {
            for (Direction d : DIRS) {
                Integer j = index().get(BlockPos.offset(pos[i], d));
                if (j != null && state[j] != BROKEN) return true;
            }
            return false;
        }

        void advance(int o, long now, boolean shy, double speed, double flowNorm, LongPredicate canGrow, List<Growth> out) {
            long lit = litUntil(shy);
            if (dead || full() || lit == Long.MAX_VALUE || now < lit + PAUSE) {
                nextAt = Double.NaN;
                seen = Math.max(seen, now);
                return;
            }
            if (Double.isNaN(nextAt)) nextAt = Math.max((double) seen, (double) lit + PAUSE) + 1 / rate(speed, flowNorm);
            while (nextAt <= now) {
                int i = candidate(canGrow);
                if (i < 0) {
                    nextAt = Double.NaN;
                    break;
                }
                state[i] = REGROWN;
                alive++;
                if (nucleus) reserve--;
                out.add(new Growth(o, pos[i]));
                if (full()) {
                    nextAt = Double.NaN;
                    break;
                }
                nextAt += 1 / rate(speed, flowNorm);
            }
            seen = Math.max(seen, now);
        }
    }

    /** One organism: its nucleus and subs. Untouched organisms are not kept, the world generated them whole. */
    public static final class Organism {
        public final long cell;
        public final String setup;
        final Organ[] organs;
        final boolean shy;
        Death death;
        double shield;
        private final boolean[] nucleus;
        // organs a nucleus's death killed before their chunks were seen
        private final boolean[] killed;
        private final double[] flow, flowNorm;

        public Organism(long cell, String setup, Organ[] organs) {
            this(cell, setup, organs, nuclei(organs), flows(organs));
        }

        /** Organs not seen yet are null: they count as alive and closed until their chunks load and fill them in. */
        public Organism(long cell, String setup, Organ[] organs, boolean[] nucleus, double[] flow) {
            this.cell = cell;
            this.setup = setup;
            this.organs = organs;
            this.shy = OrganicLight.shy(setup);
            this.death = death(setup);
            this.shield = shield(setup);
            this.killed = new boolean[organs.length];
            this.nucleus = nucleus.clone();
            this.flow = flow.clone();
            double top = 0;
            for (int i = 0; i < organs.length; i++) {
                if (!nucleus[i] && Double.isFinite(flow[i])) top = Math.max(top, flow[i]);
            }
            flowNorm = new double[organs.length];
            for (int i = 0; i < organs.length; i++) {
                flowNorm[i] = nucleus[i] || !Double.isFinite(flow[i]) || top <= 0 ? 1 : flow[i] / top;
            }
        }

        private static boolean[] nuclei(Organ[] organs) {
            boolean[] n = new boolean[organs.length];
            for (int i = 0; i < organs.length; i++) n[i] = organs[i].nucleus;
            return n;
        }

        private static double[] flows(Organ[] organs) {
            double[] f = new double[organs.length];
            for (int i = 0; i < organs.length; i++) f[i] = organs[i].flow;
            return f;
        }

        public int size() {
            return organs.length;
        }

        /** The organ, or null while its chunks have not been seen. */
        public Organ organ(int o) {
            return organs[o];
        }

        public boolean nucleus(int o) {
            return nucleus[o];
        }

        public boolean seen(int o) {
            return organs[o] != null;
        }

        /** Fills in an organ first seen now, built with the same nucleus flag and flow as the table gave. */
        public void fill(int o, Organ g) {
            if (organs[o] != null) return;
            organs[o] = g;
            if (killed[o]) {
                g.dead = true;
                g.nextAt = Double.NaN;
            }
        }

        public void kill(int o) {
            if (organs[o] == null) {
                killed[o] = true;
                return;
            }
            organs[o].dead = true;
            organs[o].nextAt = Double.NaN;
        }

        public boolean dead(int o) {
            return organs[o] == null ? killed[o] : organs[o].dead;
        }

        /** Alive while the nucleus lives, or while any sub lives once its death rule lets subs outlive it. */
        public boolean alive() {
            boolean sub = false;
            for (int o = 0; o < organs.length; o++) {
                if (nucleus[o] && !dead(o)) return true;
                if (!nucleus[o] && !dead(o)) sub = true;
            }
            return death != Death.SUBS_FIRST && sub;
        }

        public boolean subsDead() {
            for (int o = 0; o < organs.length; o++) {
                if (!nucleus[o] && !dead(o)) return false;
            }
            return true;
        }

        public boolean exposed(int o, long now) {
            return organs[o] != null && organs[o].exposed(now);
        }

        public double fed() {
            return fed(-1);
        }

        /** Live share of the subs' flow, each weighing its flow (1 when unknown), unseen subs and organ also counted alive. */
        double fed(int also) {
            double all = 0, live = 0;
            for (int o = 0; o < organs.length; o++) {
                if (nucleus[o]) continue;
                double w = Double.isFinite(flow[o]) && flow[o] > 0 ? flow[o] : 1;
                all += w;
                if (!dead(o) || o == also) live += w;
            }
            return all > 0 ? live / all : 0;
        }

        public boolean shielded(int o) {
            if (!nucleus[o]) return false;
            return death == Death.SUBS_FIRST ? !subsDead() : fed() > shield + 1e-9;
        }

        public boolean open(int o, long now) {
            return exposed(o, now) && !shielded(o);
        }

        public boolean core(int o, long p) {
            Organ g = organs[o];
            return g != null && g.core >= 0 && g.pos[g.core] == p;
        }

        public boolean breakable(int o, long now) {
            Organ g = organs[o];
            return g != null && !g.dead && open(o, now);
        }

        public Hit hit(int o, long p, long now) {
            return hit(o, p, now, false);
        }

        /** With force the block is taken even if closed: something other than a player's hand removed it. */
        public Hit hit(int o, long p, long now, boolean force) {
            Organ g = organs[o];
            if (g == null || g.dead || (!force && !breakable(o, now))) return Hit.REFUSED;
            int i = g.indexOf(p);
            if (i < 0 || g.state[i] == BROKEN) return Hit.REFUSED;
            boolean material = g.state[i] == ALIVE;
            g.state[i] = BROKEN;
            g.alive--;
            boolean died = g.nucleus ? (NUCLEUS_CORE && i == g.core) || g.alive == 0
                    : (CORE_KILL && i == g.core) || g.alive < DEATH * g.pos.length;
            if (died) {
                g.dead = true;
                g.nextAt = Double.NaN;
            }
            return new Hit(true, material, died, died && g.nucleus);
        }

        public void setBonfire(int o, boolean on, long now) {
            Organ g = organs[o];
            if (g == null) return;
            long before = g.litUntil(shy);
            boolean wasOpen = g.open(shy);
            g.bonfire = on;
            changed(g, before, wasOpen, now);
        }

        public void setLight(int o, boolean on, long now) {
            Organ g = organs[o];
            if (g == null) return;
            long before = g.litUntil(shy);
            boolean wasOpen = g.open(shy);
            g.light = on;
            changed(g, before, wasOpen, now);
        }

        /** A live flame on the bulb burns until then, a second flame only lengthens it. */
        public void setFlame(int o, long until, long now) {
            Organ g = organs[o];
            if (g == null) return;
            long before = g.litUntil(shy);
            boolean wasOpen = g.open(shy);
            g.flameUntil = Math.max(g.flameUntil, until);
            changed(g, before, wasOpen, now);
        }

        private void changed(Organ g, long before, boolean wasOpen, long now) {
            if (wasOpen && !g.open(shy)) g.openEnd = now;
            if (g.litUntil(shy) != before) g.nextAt = Double.NaN;
        }

        /** Heals every bulb up to now, one block per event, in events that do not depend on how the time was split. */
        public List<Growth> advance(long now, LongPredicate canGrow) {
            List<Growth> out = new ArrayList<>();
            double speed = speed(setup);
            for (int o = 0; o < organs.length; o++) {
                if (organs[o] != null) organs[o].advance(o, now, shy, speed, flowNorm[o], canGrow, out);
            }
            return out;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("cell", cell);
            t.putString("setup", setup);
            ListTag list = new ListTag();
            for (int o = 0; o < organs.length; o++) {
                CompoundTag g = organs[o] != null ? organs[o].save() : new CompoundTag();
                if (organs[o] == null) {
                    g.putBoolean("unseen", true);
                    g.putBoolean("nucleus", nucleus[o]);
                    if (!Double.isNaN(flow[o])) g.putDouble("flow", flow[o]);
                    if (killed[o]) g.putBoolean("killed", true);
                }
                list.add(g);
            }
            t.put("organs", list);
            return t;
        }

        public static Organism load(CompoundTag t) {
            ListTag list = t.getList("organs", Tag.TAG_COMPOUND);
            Organ[] organs = new Organ[list.size()];
            boolean[] nucleus = new boolean[organs.length];
            double[] flow = new double[organs.length];
            for (int i = 0; i < organs.length; i++) {
                CompoundTag g = list.getCompound(i);
                nucleus[i] = g.getBoolean("nucleus");
                flow[i] = g.contains("flow") ? g.getDouble("flow") : Double.NaN;
                organs[i] = g.getBoolean("unseen") ? null : new Organ(g);
            }
            Organism o = new Organism(t.getLong("cell"), t.getString("setup"), organs, nucleus, flow);
            for (int i = 0; i < organs.length; i++) o.killed[i] = organs[i] == null && list.getCompound(i).getBoolean("killed");
            return o;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "ORG organism %d %s", cell, setup));
            for (int o = 0; o < organs.length; o++) {
                Organ g = organs[o];
                if (g == null) {
                    sb.append(nucleus[o] ? " | nucleus unseen" : " | sub unseen");
                    continue;
                }
                sb.append(String.format(Locale.ROOT, " | %s %d/%d%s", g.nucleus ? "nucleus" : "sub", g.alive, g.pos.length,
                        g.dead ? " dead" : ""));
            }
            return sb.toString();
        }
    }

    /** The organisms players have touched by cell, the bonfires lit by hand and the bulbs each bonfire's light exposes. */
    public static final class Data extends SavedData {
        public static final String NAME = "the_beyond_org_life";
        final Map<Long, Organism> organisms = new TreeMap<>();
        final Map<Long, int[]> handLit = new TreeMap<>();
        final Map<Long, List<long[]>> exposedBy = new TreeMap<>();
        final Map<Long, long[]> released = new TreeMap<>();
        final Map<Long, Long> broken = new TreeMap<>();

        public static SavedData.Factory<Data> factory() {
            return new SavedData.Factory<>(Data::new, Data::load, null);
        }

        public static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(factory(), NAME);
        }

        public Organism get(long cell) {
            return organisms.get(cell);
        }

        public void put(Organism o) {
            organisms.put(o.cell, o);
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Organism o : organisms.values()) list.add(o.save());
            tag.put("organisms", list);
            ListTag lit = new ListTag();
            for (Map.Entry<Long, int[]> e : handLit.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putLong("fire", e.getKey());
                t.putIntArray("at", e.getValue().clone());
                List<long[]> ex = exposedBy.getOrDefault(e.getKey(), List.of());
                long[] flat = new long[ex.size() * 2];
                for (int i = 0; i < ex.size(); i++) {
                    flat[2 * i] = ex.get(i)[0];
                    flat[2 * i + 1] = ex.get(i)[1];
                }
                t.putLongArray("exposed", flat);
                t.putLongArray("released", released.getOrDefault(e.getKey(), new long[0]).clone());
                lit.add(t);
            }
            tag.put("handLit", lit);
            long[] br = new long[broken.size() * 2];
            int k = 0;
            for (Map.Entry<Long, Long> e : broken.entrySet()) {
                br[k++] = e.getKey();
                br[k++] = e.getValue();
            }
            tag.putLongArray("broken", br);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag t : tag.getList("organisms", Tag.TAG_COMPOUND)) {
                Organism o = Organism.load((CompoundTag) t);
                d.organisms.put(o.cell, o);
            }
            for (Tag t : tag.getList("handLit", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                long fire = c.getLong("fire");
                d.handLit.put(fire, c.getIntArray("at").clone());
                long[] flat = c.getLongArray("exposed");
                List<long[]> ex = new ArrayList<>();
                for (int i = 0; i + 1 < flat.length; i += 2) ex.add(new long[]{flat[i], flat[i + 1]});
                if (!ex.isEmpty()) d.exposedBy.put(fire, ex);
                long[] rel = c.getLongArray("released");
                if (rel.length > 0) d.released.put(fire, rel.clone());
            }
            long[] br = tag.getLongArray("broken");
            for (int i = 0; i + 1 < br.length; i += 2) d.broken.put(br[i], br[i + 1]);
            return d;
        }
    }
}
