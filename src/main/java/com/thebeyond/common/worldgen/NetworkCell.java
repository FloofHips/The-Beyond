package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/** One cell grown from its nucleus to its sub-nuclei, as a tree or a slime mold mesh. */
abstract class NetworkCell extends NetworkSlimeMold {
    NetworkCell(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    void growCell(int ci, boolean[] hasTree, List<List<Integer>> treeVox) {
        Core core = cores.get(ci);
        List<Integer> hab = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            if (nLab[u] == ci && (nFlag[u] & F_HAB) != 0) hab.add(u);
        }
        if (hab.isEmpty()) {
            report.append(String.format(Locale.ROOT, " | cell %d: no substrate", core.id()));
            return;
        }
        double[] prio = new double[hab.size()];
        int nuc = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < hab.size(); i++) {
            int u = hab.get(i);
            prio[i] = sq(nX[u] + 0.5 - core.x(), nY[u] + 0.5 - core.y(), nZ[u] + 0.5 - core.z());
            if (prio[i] < best) {
                best = prio[i];
                nuc = u;
            }
        }
        final int nucF = nuc;
        double[] prio2 = new double[hab.size()];
        for (int i = 0; i < hab.size(); i++) {
            int u = hab.get(i);
            prio2[i] = sq(nX[u] - nX[nucF], nY[u] - nY[nucF], nZ[u] - nZ[nucF]);
        }
        List<Integer> subs = new ArrayList<>();
        for (int u : poisson(hab, forageSpacing(hab, st.rSub()), st.rSub(), new SplittableRandom(st.seed() + core.id()), prio2)) {
            if (Math.sqrt(sq(nX[u] - nX[nuc], nY[u] - nY[nuc], nZ[u] - nZ[nuc])) >= st.rSub() * 0.6) subs.add(u);
            if (subs.size() >= st.mSub()) break;
        }
        boolean[] own = new boolean[cores.size()];
        own[ci] = true;
        int base = -1;
        int par = core.parent();
        String cross = "none";
        if (par >= 0 && hasTree[par] && st.bio().on()) {
            boolean[] both = own.clone();
            both[par] = true;
            Link lk = fusionLink(both, treeVox.get(par), nuc, v -> dn[noVox.get(vk(par, v))], coreDn(par), ci);
            if (lk != null) {
                base = join(lk.path(), ci, noVox.get(vk(par, lk.src())), K_CROSS, 0);
                cross = String.format(Locale.ROOT, "%.0f", lk.cost());
            }
        } else if (par >= 0 && hasTree[par]) {
            boolean[] both = own.clone();
            both[par] = true;
            Search s = new Search(both, st.reachX());
            for (int v : treeVox.get(par)) s.source(v);
            s.run();
            if (Double.isFinite(s.dist[nuc])) {
                List<Integer> p = s.path(nuc);
                int from = p.isEmpty() ? nuc : s.src[nuc];
                Integer anchor = noVox.get(vk(par, from));
                if (anchor != null) {
                    base = join(p, ci, anchor, K_CROSS, 0);
                    cross = String.format(Locale.ROOT, "%.0f", s.dist[nuc]);
                }
            }
        }
        if (par >= 0 && !hasTree[par] && extVox != null && extVox.length > 0) {
            boolean[] both = own.clone();
            both[par] = true;
            Search s = new Search(both, st.reachX());
            Map<Integer, Float> startD = new HashMap<>(), startR = new HashMap<>();
            Map<Integer, Float> startA = new HashMap<>(), startN = new HashMap<>(), startS = new HashMap<>();
            for (int k = 0; k < extVox.length; k++) {
                int u = nodeAt(extVox[k]);
                if (u < 0 || nLab[u] != par) continue;
                s.source(u);
                startD.put(u, extD[k]);
                startR.put(u, extR != null && k < extR.length ? extR[k] : Float.NaN);
                startA.put(u, extA != null && k < extA.length ? extA[k] : Float.NaN);
                startN.put(u, extN != null && k < extN.length ? extN[k] : Float.NaN);
                startS.put(u, extS != null && k < extS.length ? extS[k] : Float.NaN);
            }
            if (Double.isNaN(extCoreD)) {
                extCoreD = Double.POSITIVE_INFINITY;
                for (float d : extD) extCoreD = Math.min(extCoreD, d);
            }
            if (st.bio().on()) {
                List<Integer> srcs = new ArrayList<>(startD.keySet());
                java.util.Collections.sort(srcs);
                Link lk = fusionLink(both, srcs, nuc, v -> startD.get(v), extCoreD, ci);
                if (lk != null) {
                    int from = lk.src();
                    int anchor = add(nX[from] + 0.5, nY[from] + 0.5, nZ[from] + 0.5, -1, K_CROSS, ci, startD.get(from), 0,
                            startR.get(from));
                    outAnchors.add(new double[]{anchor, startA.get(from), startN.get(from), startS.get(from)});
                    base = join(lk.path(), ci, anchor, K_CROSS, 0);
                    cross = String.format(Locale.ROOT, "%.0f (outside)", lk.cost());
                }
            } else {
                s.run();
                if (Double.isFinite(s.dist[nuc]) && s.src[nuc] >= 0 && s.src[nuc] != nuc) {
                    int from = s.src[nuc];
                    int anchor = add(nX[from] + 0.5, nY[from] + 0.5, nZ[from] + 0.5, -1, K_CROSS, ci, startD.get(from), 0,
                            Double.NaN);
                    base = join(s.path(nuc), ci, anchor, K_CROSS, 0);
                    cross = String.format(Locale.ROOT, "%.0f (outside)", s.dist[nuc]);
                }
            }
        }
        if (base < 0) base = add(nX[nuc] + 0.5, nY[nuc] + 0.5, nZ[nuc] + 0.5, -1, K_CORE, ci, 0, 0, Double.NaN);
        else kind[base] = K_CORE;
        linked |= !cross.equals("none");
        peso[base] += st.wSub();
        noVox.put(vk(ci, nuc), base);
        List<Integer> tree = treeVox.get(ci);
        tree.add(nuc);
        int linked = 0, far, colonies = 0;
        List<Integer> colonyRoots = new ArrayList<>();
        if (st.style() == Style.MESH) {
            int[] r = mesh(ci, nuc, subs, base, own, tree);
            linked = r[0];
            far = r[1] - fuseOwn(ci, List.of(), farSubs);
        } else {
            List<Integer> rest = new ArrayList<>(subs);
            linked = steiner(ci, base, tree, rest, own, true, 1);
            far = rest.size();
            while (st.colonies() && !rest.isEmpty()) {
                int root = rest.get(0);
                double bd = Double.MAX_VALUE;
                for (int u : rest) {
                    double d = sq(nX[u] - nX[nuc], nY[u] - nY[nuc], nZ[u] - nZ[nuc]);
                    if (d < bd) {
                        bd = d;
                        root = u;
                    }
                }
                rest.remove((Integer) root);
                int cb = add(nX[root] + 0.5, nY[root] + 0.5, nZ[root] + 0.5, -1, K_SUB, ci, st.reach(), st.wSub(), Double.NaN);
                colonyRoots.add(cb);
                noVox.put(vk(ci, root), cb);
                List<Integer> col = new ArrayList<>();
                col.add(root);
                steiner(ci, cb, col, rest, own, false, st.bio().vigReach() ? Math.pow(st.vSub(), st.bio().bReach()) : 1);
                colonies++;
            }
            far = rest.size() - fuseOwn(ci, colonyRoots, rest);
        }
        hasTree[ci] = true;
        report.append(String.format(Locale.ROOT, " | cell %d: nucleus %d %d %d, %d subs, %d linked, %d colonies, %d far,"
                        + " link to parent %s", core.id(), nX[nuc] + g.x0, nY[nuc] + g.y0, nZ[nuc] + g.z0, subs.size(), linked,
                colonies, far, cross));
    }

    /** Incremental shortest-path Steiner tree, a colony born of a sub reaching vr of the nucleus's reach and ligaments. */
    int steiner(int ci, int base, List<Integer> tree, List<Integer> rest, boolean[] allowed, boolean reachRule,
                        double vr) {
        Search s = new Search(allowed, st.reach() * vr);
        if (vr < 1) s.linkCap(st.linkMax() * vr);
        for (int v : tree) s.source(v);
        s.run();
        int linked = 0;
        while (!rest.isEmpty()) {
            int pick = -1;
            double pd = Double.MAX_VALUE;
            for (int u : rest) {
                double d = s.dist[u];
                if (!Double.isFinite(d)) continue;
                if (reachRule) {
                    Integer at = noVox.get(vk(ci, s.src[u]));
                    if (at == null || dn[at] - dn[base] + d > st.reach()) continue;
                }
                if (d < pd) {
                    pd = d;
                    pick = u;
                }
            }
            if (pick < 0) break;
            List<Integer> p = s.path(pick);
            Integer at = noVox.get(vk(ci, s.src[pick]));
            int end = join(p, ci, at, K_TRUNK, st.wSub());
            kind[end] = K_SUB;
            for (int v : p) {
                tree.add(v);
                s.source(v);
            }
            s.run();
            rest.remove((Integer) pick);
            linked++;
        }
        return linked;
    }
}
