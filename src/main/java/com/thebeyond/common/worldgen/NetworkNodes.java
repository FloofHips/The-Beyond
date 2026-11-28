package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** The network nodes, their joins and fusions with the own organism and the parent cell. */
abstract class NetworkNodes extends NetworkPaths {
    NetworkNodes(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    int add(double x, double y, double z, int parent, byte k, int c, double d, double w, double r) {
        if (m == px.length) {
            int cap = m * 2;
            px = Arrays.copyOf(px, cap);
            py = Arrays.copyOf(py, cap);
            pz = Arrays.copyOf(pz, cap);
            dn = Arrays.copyOf(dn, cap);
            peso = Arrays.copyOf(peso, cap);
            rf = Arrays.copyOf(rf, cap);
            pai = Arrays.copyOf(pai, cap);
            cel = Arrays.copyOf(cel, cap);
            kind = Arrays.copyOf(kind, cap);
            via = Arrays.copyOf(via, cap);
        }
        px[m] = x;
        py[m] = y;
        pz[m] = z;
        pai[m] = parent;
        kind[m] = k;
        cel[m] = c;
        dn[m] = d;
        peso[m] = w;
        rf[m] = r;
        via[m] = false;
        return m++;
    }

    /** Joins graph nodes as a chain of network nodes from base, and a hop longer than a block is a ligament curve. */
    int join(List<Integer> path, int ci, int base, byte k, double endWeight) {
        int prev = base, n0 = m;
        double d = dn[base];
        double[] pp = {px[base], py[base], pz[base]};
        for (int u : path) {
            double[] q = center(u);
            double L = dist(q, pp);
            if (L > 1.8) {
                double[] a = pp, b = q;
                boolean swap = swapEnds(a, b);
                if (swap) {
                    a = q;
                    b = pp;
                }
                boolean pillar = steep(a, b, st.linkAng());
                int steps = Math.max(2, (int) L);
                for (int s = 1; s <= steps; s++) {
                    double t = swap ? 1 - (double) s / steps : (double) s / steps;
                    double[] p = s == steps ? q : linkPoint(a, b, t);
                    d += Math.sqrt(sq(p[0] - px[prev], p[1] - py[prev], p[2] - pz[prev]));
                    prev = add(p[0], p[1], p[2], prev, pillar ? K_PILLAR : K_CABLE, ci, d, 0, profile(pillar, t));
                }
                links.add(new double[]{pillar ? 1 : 0, a[0], a[1], a[2], b[0], b[1], b[2], Double.NaN});
            } else {
                d += L;
                prev = add(q[0], q[1], q[2], prev, k, ci, d, 0, Double.NaN);
            }
            noVox.put(vk(ci, u), prev);
            pp = q;
        }
        peso[prev] += endWeight;
        for (int i = n0; i < m; i++) via[i] = true;
        return prev;
    }

    record Link(List<Integer> path, int src, double cost) {}

    /** Joins a stray colony or far sub to its organism within fusD, over the substrate when it can, else through clear air. */
    int fuseOwn(int ci, List<Integer> colonyRoots, List<Integer> far) {
        double D = st.bio().fusD();
        if (!st.bio().on() || D <= 0 || (colonyRoots.isEmpty() && far.isEmpty())) return 0;
        int[] top = new int[m];
        for (int i = 0; i < m; i++) top[i] = pai[i] < 0 ? i : top[pai[i]];
        java.util.Set<Integer> live = new java.util.HashSet<>(), roots = new java.util.HashSet<>(colonyRoots);
        for (int[] e : meshEdges) {
            if (e[2] == FILM) continue;
            live.add(e[0]);
            live.add(e[1]);
        }
        boolean[] organ = new boolean[m];
        OrganicSpatialHash net = new OrganicSpatialHash(4);
        for (int i = 0; i < m; i++) {
            // in the slime mold only a live tube is the organism, a dead one is a trail of film
            organ[i] = cel[i] == ci && !roots.contains(top[i]) && (st.style() != Style.MESH || live.contains(i) || via[i]
                    || kind[i] == K_CORE || kind[i] == K_SUB);
            if (organ[i]) net.add(i, px[i], py[i], pz[i]);
        }
        Map<Integer, List<Integer>> colVox = new TreeMap<>();
        boolean[] all = new boolean[cores.size()];
        Arrays.fill(all, true);
        Search sr = new Search(all, D).geometric();
        for (Map.Entry<Long, Integer> en : new TreeMap<>(noVox).entrySet()) {
            if ((int) (en.getKey() >>> 32) != ci) continue;
            int u = (int) (long) en.getKey(), id = en.getValue();
            if (id < 0 || id >= m) continue;
            if (organ[id]) sr.source(u);
            else if (roots.contains(top[id])) colVox.computeIfAbsent(top[id], k -> new ArrayList<>()).add(u);
        }
        sr.run();
        List<int[]> targets = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> en : colVox.entrySet()) {
            for (int u : en.getValue()) targets.add(new int[]{en.getKey(), u});
        }
        for (int u : far) targets.add(new int[]{-1, u});
        int done = 0;
        java.util.Set<Integer> fused = new java.util.HashSet<>();
        for (int t0 = 0; t0 < targets.size(); ) {
            int key = targets.get(t0)[0], t1 = t0;
            List<Integer> gs = new ArrayList<>();
            while (t1 < targets.size() && targets.get(t1)[0] == key && (key >= 0 || t1 == t0)) gs.add(targets.get(t1++)[1]);
            t0 = t1;
            int xg = -1, y = -1;
            double best = Double.POSITIVE_INFINITY;
            List<Integer> cam = null;
            String how = "substrate";
            for (int u : gs) {
                if (sr.dist[u] < best) {
                    best = sr.dist[u];
                    xg = u;
                }
            }
            if (xg >= 0) {
                cam = sr.path(xg);
                y = noVox.get(vk(ci, sr.src[xg]));
            } else {
                List<double[]> cand = new ArrayList<>();
                for (int u : gs) {
                    double[] c = center(u);
                    int j = net.nearest(c[0], c[1], c[2], D, this::nodePos);
                    if (j >= 0) cand.add(new double[]{dist(c, nodePos(j)), u, j});
                }
                cand.sort((a, b) -> Double.compare(a[0], b[0]));
                for (int k = 0; k < Math.min(8, cand.size()); k++) {
                    double[] a = nodePos((int) cand.get(k)[2]), b = center((int) cand.get(k)[1]);
                    double[] hi = swapEnds(a, b) ? b : a, lo = hi == a ? b : a;
                    if (!clearCurve(hi, lo, cand.get(k)[0])) continue;
                    best = cand.get(k)[0];
                    xg = (int) cand.get(k)[1];
                    y = (int) cand.get(k)[2];
                    cam = List.of(xg);
                    how = "air";
                    break;
                }
            }
            if (cam == null || cam.isEmpty() || y < 0) continue;
            int n0 = m;
            if (key >= 0) {
                int x = noVox.get(vk(ci, xg));
                join(cam, ci, y, K_TRUNK, 0);
                noVox.put(vk(ci, xg), x);
                fusions.add(new int[]{key, n0, m - 1, y, x});
            } else {
                int end = join(cam, ci, y, K_TRUNK, st.wSub());
                kind[end] = K_SUB;
                fusions.add(new int[]{end, n0, end > n0 ? end - 1 : -1, y, end});
                fused.add(xg);
            }
            done++;
            report.append(String.format(Locale.ROOT, " | fusion %s %.1f blocks through the %s", key >= 0 ? "colony" : "sub",
                    best, how));
        }
        far.removeIf(fused::contains);
        return fused.size();
    }

    double coreDn(int c) {
        for (int i = 0; i < m; i++) {
            if (kind[i] == K_CORE && cel[i] == c) return dn[i];
        }
        return 0;
    }

    /** Cost of the hop a to b as the search walks it (graph neighbours or a ligament). */
    double hopCost(Search s, int a, int b) {
        int dy = nY[b] - nY[a];
        if (Math.abs(nX[b] - nX[a]) <= 1 && Math.abs(dy) <= 1 && Math.abs(nZ[b] - nZ[a]) <= 1) {
            double len = Math.sqrt(sq(nX[b] - nX[a], dy, nZ[b] - nZ[a]));
            return len * 0.5 * (nMult[a] + nMult[b]) * s.up(dy / len, false);
        }
        for (double[] e : linkAdj.getOrDefault(a, List.of())) {
            if ((int) e[0] == b) return e[1] * s.up(dy / e[2], true);
        }
        return Double.POSITIVE_INFINITY;
    }

    /** A link grown from both ends and joined where the summed cost is least, its meeting point the youngest part. */
    Link fusionLink(boolean[] both, List<Integer> sources, int nuc, java.util.function.IntToDoubleFunction srcDn,
                            double parCoreDn, int ci) {
        Search sp = new Search(both, st.reachX()), sc = new Search(both, st.reachX());
        for (int v : sources) sp.source(v);
        sp.run();
        sc.source(nuc);
        sc.run();
        int mid = -1;
        double tot = Double.POSITIVE_INFINITY;
        for (int v = 0; v < n; v++) {
            double t = sp.dist[v] + sc.dist[v];
            if (t < tot) {
                tot = t;
                mid = v;
            }
        }
        if (mid < 0) return null;
        List<Integer> seq = new ArrayList<>();
        int src = sp.src[mid];
        seq.add(src);
        seq.addAll(sp.path(mid));
        for (int v = sc.pred[mid]; v >= 0; v = sc.pred[v]) seq.add(v);
        int L = seq.size();
        double[] cp = new double[L], cf = new double[L], sg = new double[L];
        for (int q = 1; q < L; q++) {
            cp[q] = cp[q - 1] + hopCost(sp, seq.get(q - 1), seq.get(q));
            sg[q] = sg[q - 1] + dist(center(seq.get(q)), center(seq.get(q - 1)));
        }
        for (int q = L - 2; q >= 0; q--) cf[q] = cf[q + 1] + hopCost(sp, seq.get(q + 1), seq.get(q));
        double vPar = Math.pow(1 / (1 + Math.max(srcDn.applyAsDouble(src) - parCoreDn, 0) / st.fluxL0()), st.bio().vFront());
        int qm = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int q = 0; q < L; q++) {
            double e = Math.abs(cp[q] / vPar - cf[q]);
            if (e < best) {
                best = e;
                qm = q;
            }
        }
        if (st.bio().vigReach()) {
            double alc = st.reachX() * Math.pow(vPar, st.bio().bReach());
            while (qm > 0 && sg[qm] > alc) qm--;
            if (sg[L - 1] - sg[qm] > st.reachX()) {
                report.append(String.format(Locale.ROOT, " | link to parent: the parent's front reaches %.0f, the child's does"
                        + " not cover %.0f", alc, sg[L - 1] - sg[qm]));
                return null;
            }
        }
        meet.put(ci, sg[qm] / Math.max(sg[L - 1], 1e-6));
        report.append(String.format(Locale.ROOT, " | link to parent by fusion: %.0f blocks, vigor %.2f, meeting at %.0f%% from"
                + " the parent", sg[L - 1], vPar, 100 * sg[qm] / Math.max(sg[L - 1], 1e-6)));
        return new Link(new ArrayList<>(seq.subList(1, L)), src, tot);
    }
}
