package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Organic network of the bog: roots (TREE) or slime-mold tubes (MESH) grown on the rock faces of its cells. */
public final class OrganicNetwork extends NetworkRaster {

    private OrganicNetwork(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    /** A parent grown in another box: grid indices of its trunks with distance, radius, flow, nucleus flow and root distance. */
    public record Parent(int[] vox, float[] d, double coreD, float[] r, float[] a, float[] n, float[] s) {
        public static final Parent NONE = new Parent(null, null, Double.NaN, null, null, null, null);
    }

    public static Result grow(Grid g, Settings st, List<Core> cores) {
        return grow(g, st, cores, Parent.NONE, IC0, null);
    }

    /** Grows beside its parent with the solver chosen. After deaths at deadNodes, only the roots that fed them change. */
    public static Result grow(Grid g, Settings st, List<Core> cores, Parent parent, boolean ic0, int[] deadNodes) {
        OrganicNetwork o = new OrganicNetwork(g, st, cores, parent.vox(), parent.d(), parent.coreD());
        o.extR = parent.r();
        o.extA = parent.a();
        o.extN = parent.n();
        o.extS = parent.s();
        o.ic0 = ic0;
        o.deadNodes = deadNodes;
        return o.run();
    }

    private Result run() {
        long t0 = System.nanoTime();
        buildNodes();
        long t1 = System.nanoTime();
        if (st.ligaments()) ligaments();
        long t2 = System.nanoTime();
        int[] order = cellOrder();
        boolean[] hasTree = new boolean[cores.size()];
        List<List<Integer>> treeVox = new ArrayList<>();
        for (int i = 0; i < cores.size(); i++) treeVox.add(new ArrayList<>());
        for (int ci : order) {
            if (cores.get(ci).grow()) growCell(ci, hasTree, treeVox);
        }
        long t3 = System.nanoTime();
        int trunks = m;
        for (int ci : order) {
            if (hasTree[ci]) fringe(ci, trunks);
        }
        prune();
        long t4 = System.nanoTime();
        Result r = raster();
        long t5 = System.nanoTime();
        List<Integer> tv = new ArrayList<>();
        List<Float> td = new ArrayList<>(), trr = new ArrayList<>(), ta = new ArrayList<>(), tn = new ArrayList<>();
        List<Float> ts = new ArrayList<>();
        for (int ci : order) {
            for (int u : treeVox.get(ci)) {
                Integer id = noVox.get(vk(ci, u));
                if (id == null || id < 0) continue;
                tv.add(nVox[u]);
                td.add((float) dn[id]);
                trr.add((float) nodeR[id]);
                ta.add(accOut == null ? Float.NaN : (float) accOut[id]);
                tn.add(normOut == null ? Float.NaN : (float) normOut[id]);
                ts.add(distOut == null ? Float.NaN : (float) distOut[id]);
            }
        }
        int[] tree = tv.stream().mapToInt(Integer::intValue).toArray();
        int nt = td.size();
        float[] treeD = new float[nt], treeR = new float[nt], treeA = new float[nt], treeN = new float[nt], treeS = new float[nt];
        for (int i = 0; i < treeD.length; i++) {
            treeD[i] = td.get(i);
            treeR[i] = trr.get(i);
            treeA[i] = ta.get(i);
            treeN[i] = tn.get(i);
            treeS[i] = ts.get(i);
        }
        report.append(String.format(Locale.ROOT, " | ms: nodes %.0f ligaments %.0f trunks %.0f fringe %.0f raster %.0f",
                (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 1e6, (t5 - t4) / 1e6));
        return new Result(r.voxels(), r.kinds(), r.dnet(), tree, treeD, treeR, treeA, treeN, treeS, report.toString(),
                r.nodes(), linked, r.owner(), r.organs(), r.thick());
    }
}
