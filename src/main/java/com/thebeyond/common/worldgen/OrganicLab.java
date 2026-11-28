package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.OrganicGrowth.Grown;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import net.minecraft.world.level.block.state.BlockState;

import static com.thebeyond.common.worldgen.OrganicExperiment.state;

/** Lab only: dumps and the twin check of the two Tero solvers, off unless their switches are set. */
final class OrganicLab {
    static final boolean DUMP = Boolean.getBoolean("beyond.sub.org.dump");
    static final boolean TERO_CHECK = Boolean.getBoolean("beyond.sub.org.teroCheck");
    static final boolean DUMP_GROWN = Boolean.getBoolean("beyond.sub.org.dumpGrown");
    static final boolean NODE_CHECK = Boolean.getBoolean("beyond.sub.org.nodeCheck");
    static final boolean TERO_STATS = Boolean.getBoolean("beyond.sub.org.teroStats");
    static volatile boolean keepNodes;
    static final Map<Long, FutureTask<Grown>> TWIN = new ConcurrentHashMap<>();

    private OrganicLab() {}

    /** Logs how the twin's network differs from the painted one, as the blocks each would paint. */
    static void compareTwin(SubSites.Cell c, Grown a, Grown b) {
        Map<Integer, BlockState> pa = painted(a), pb = painted(b);
        int onlyA = 0, onlyB = 0, changed = 0;
        for (Map.Entry<Integer, BlockState> e : pa.entrySet()) {
            BlockState o = pb.get(e.getKey());
            if (o == null) onlyA++;
            else if (o != e.getValue()) changed++;
        }
        for (Integer i : pb.keySet()) if (!pa.containsKey(i)) onlyB++;
        boolean same = Arrays.equals(a.vox(), b.vox()) && Arrays.equals(a.kinds(), b.kinds())
                && Arrays.equals(a.dnet(), b.dnet());
        boolean trunks = Arrays.equals(a.treeWorld(), b.treeWorld()) && Arrays.equals(a.treeD(), b.treeD())
                && Arrays.equals(a.treeR(), b.treeR()) && Arrays.equals(a.treeA(), b.treeA())
                && Arrays.equals(a.treeN(), b.treeN()) && Arrays.equals(a.treeS(), b.treeS());
        TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG tero check cell %d %d %d | blocks %d twin %d | only painted %d"
                + " only twin %d changed %d | identical %b trunks %b", c.lx(), c.ly(), c.lz(), pa.size(), pb.size(), onlyA,
                onlyB, changed, same, trunks));
    }

    private static Map<Integer, BlockState> painted(Grown g) {
        Map<Integer, BlockState> m = new HashMap<>();
        for (int s = 0; s < g.nNet(); s++) m.put(g.vox()[s], state(g.kinds()[s], g.dnet()[s], false));
        return m;
    }

    static void dumpGrown(SubSites.Cell c, SubSites.Cell parent, boolean twin, int x0, int y0, int z0,
                                  OrganicNetwork.Grid g, OrganicNetwork.Result r, int[] ext, int nNet) {
        String f = String.format(Locale.ROOT, "org_grown_%s_%d_%d_%d.bin", twin ? "twin" : "full", c.lx(), c.ly(), c.lz());
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(Path.of(f))))) {
            int[] head = {x0, y0, z0, g.nx, g.ny, g.nz, parent == null ? 0 : 1, parent == null ? 0 : parent.lx(),
                    parent == null ? 0 : parent.ly(), parent == null ? 0 : parent.lz(), r.linked() ? 1 : 0, nNet};
            for (int v : head) out.writeInt(v);
            out.writeDouble(c.x());
            out.writeDouble(c.y());
            out.writeDouble(c.z());
            for (int[] a : new int[][]{r.voxels(), r.tree(), ext == null ? new int[0] : ext}) {
                out.writeInt(a.length);
                for (int v : a) out.writeInt(v);
            }
            out.writeInt(r.kinds().length);
            out.write(r.kinds());
        } catch (Exception ex) {
            TheBeyond.LOGGER.error("ORG_FAILURE grown dump {}", f, ex);
        }
    }
}
