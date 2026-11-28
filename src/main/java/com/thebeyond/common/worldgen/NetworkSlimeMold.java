package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;

/** The slime mold's tubes on a Poisson mesh, adapted to the flux between its foods (Tero 2010). */
abstract class NetworkSlimeMold extends NetworkNodes {
    NetworkSlimeMold(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    int[] mesh(int ci, int nuc, List<Integer> subs, int base, boolean[] own, List<Integer> tree) {
        Search reach = new Search(own, st.reach());
        reach.source(nuc);
        reach.run();
        List<Integer> hug = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            if (Double.isFinite(reach.dist[u]) && (nFlag[u] & F_HUG) != 0) hug.add(u);
        }
        int[] M = poisson(hug, st.meshR(), new SplittableRandom(st.seed() + 300 + ci), null);
        int mm = M.length;
        Map<Integer, Integer> num = new HashMap<>();
        for (int j = 0; j < mm; j++) num.put(M[j], j);
        Search geo = new Search(own, Double.POSITIVE_INFINITY).undirected();
        for (int v : M) geo.source(v);
        geo.run();
        // geodesic neighbours: graph edges whose ends grew from different mesh nodes
        Map<Long, double[]> pairs = new HashMap<>();
        for (int u = 0; u < n; u++) {
            if (!own[nLab[u]] || geo.src[u] < 0 || !Double.isFinite(reach.dist[u])) continue;
            int x = nX[u], y = nY[u], z = nZ[u];
            List<double[]> nb = new ArrayList<>();
            for (int k = 0; k < 26; k++) {
                int qx = x + OFF26[k][0], qy = y + OFF26[k][1], qz = z + OFF26[k][2];
                if (!g.inside(qx, qy, qz)) continue;
                int v = nodeAt(qx, qy, qz);
                if (v >= 0) nb.add(new double[]{v, LEN26[k] * 0.5 * (nMult[u] + nMult[v])});
            }
            List<double[]> extra = linkAdj.get(u);
            if (extra != null) nb.addAll(extra);
            for (double[] e : nb) {
                int v = (int) e[0];
                if (v <= u && linkAdj.get(u) == null) continue;
                if (!own[nLab[v]] || geo.src[v] < 0 || geo.src[v] == geo.src[u] || !Double.isFinite(reach.dist[v])) continue;
                int a = num.get(geo.src[u]), b = num.get(geo.src[v]);
                double tot = geo.dist[u] + e[1] + geo.dist[v];
                long kk = (long) Math.min(a, b) * mm + Math.max(a, b);
                double[] old = pairs.get(kk);
                if (old == null || tot < old[0]) pairs.put(kk, new double[]{tot, a < b ? u : v, a < b ? v : u});
            }
        }
        int ne = pairs.size();
        int[] ea = new int[ne], eb = new int[ne], eu = new int[ne], ev = new int[ne];
        double[] el = new double[ne];
        int e0 = 0;
        for (Map.Entry<Long, double[]> en : new TreeMap<>(pairs).entrySet()) {
            ea[e0] = (int) (en.getKey() / mm);
            eb[e0] = (int) (en.getKey() % mm);
            el[e0] = Math.max(1e-6, en.getValue()[0]);
            eu[e0] = (int) en.getValue()[1];
            ev[e0] = (int) en.getValue()[2];
            e0++;
        }
        // foods: the nucleus and the reachable sub-nuclei, at their nearest mesh node
        Map<Integer, Byte> food = new HashMap<>();
        int nucM = nearestMesh(M, nuc);
        food.put(nucM, K_CORE);
        int linked = 0, far = 0;
        farSubs.clear();
        for (int s : subs) {
            int j = nearestMesh(M, s);
            if (Double.isFinite(reach.dist[s]) && !food.containsKey(j)) {
                food.put(j, K_SUB);
                linked++;
            } else {
                far++;
                if (!Double.isFinite(reach.dist[s])) farSubs.add(s);
            }
        }
        int[] comp = components(mm, ea, eb);
        List<Integer> foods = new ArrayList<>();
        for (int j : food.keySet()) {
            if (comp[j] == comp[nucM]) foods.add(j);
        }
        java.util.Collections.sort(foods);
        double[] D = new double[ne];
        if (foods.size() >= 2) tero(mm, ea, eb, el, comp, comp[nucM], foods, nucM, D, new SplittableRandom(st.seed() + ci));
        // drawn edges: geodesic polylines, live tubes by flux and thin film for the barely used
        double[][] dv = meshTree(mm, ea, eb, D, nucM, el);
        Map<Integer, Integer> rid = new HashMap<>();
        Integer[] byDist = new Integer[mm];
        for (int j = 0; j < mm; j++) byDist[j] = j;
        Arrays.sort(byDist, (a, b) -> Double.compare(dv[0][a], dv[0][b]));
        double d0 = dn[base] + Math.sqrt(sq(nX[M[nucM]] + 0.5 - px[base], nY[M[nucM]] + 0.5 - py[base], nZ[M[nucM]] + 0.5 - pz[base]));
        for (int j : byDist) {
            if (!Double.isFinite(dv[0][j])) continue;
            int parent = j == nucM ? base : rid.get((int) dv[1][j]);
            byte k = food.containsKey(j) && j != nucM ? food.get(j) : K_TRUNK;
            int id = add(nX[M[j]] + 0.5, nY[M[j]] + 0.5, nZ[M[j]] + 0.5, parent, k, ci, d0 + dv[0][j], 0, Double.NaN);
            rid.put(j, id);
            noVox.put(vk(ci, M[j]), id);
            tree.add(M[j]);
        }
        if (rid.containsKey(nucM)) addMeshEdge(base, rid.get(nucM), st.rMax(), TUBE);
        Map<Integer, Integer> voxNode = new HashMap<>();
        for (Map.Entry<Integer, Integer> en : rid.entrySet()) voxNode.put(nVox[M[en.getKey()]], en.getValue());
        Integer[] eOrder = new Integer[ne];
        for (int e = 0; e < ne; e++) eOrder[e] = e;
        Arrays.sort(eOrder, (a, b) -> Double.compare(D[b], D[a]));
        int live = 0, thin = 0;
        for (int e : eOrder) {
            boolean alive = D[e] >= st.tKeep(), film = !alive && D[e] >= st.tKeep() / 6;
            if (!alive && !film) continue;
            Integer ra = rid.get(ea[e]), rb = rid.get(eb[e]);
            if (ra == null || rb == null) continue;
            if (alive) live++;
            else thin++;
            double re = meshRadius(D[e]);
            List<double[]> poly = polyline(geo, eu[e], ev[e], M[ea[e]], re, alive || !st.flux());
            double da = dn[ra], db = dn[rb], arc = 0;
            for (int i = 1; i < poly.size(); i++) arc += dist(poly.get(i), poly.get(i - 1));
            int prev = ra;
            double s = 0;
            for (int i = 1; i < poly.size() - 1; i++) {
                double[] q = poly.get(i);
                s += dist(q, poly.get(i - 1));
                boolean aerial = q[3] > 0;
                int vox = g.index((int) Math.floor(q[0]), (int) Math.floor(q[1]), (int) Math.floor(q[2]));
                Integer nd = aerial ? null : voxNode.get(vox);
                if (nd == null) {
                    nd = add(q[0], q[1], q[2], prev, aerial ? (q[3] > 1 ? K_PILLAR : K_CABLE) : alive ? K_TRUNK : K_FRINGE,
                            ci, Math.min(da + s, db + arc - s), 0, Double.NaN);
                    if (!aerial) voxNode.put(vox, nd);
                }
                double rr = alive ? (Double.isFinite(q[4]) ? linkR(q[4], re) : re) : 1.2;
                if (st.bio().on() && alive && Double.isFinite(q[5])) rr = q[4] * colarPt(Math.max(st.linkRMin(), re), q[5], q[6]);
                if (nd != prev) addMeshEdge(prev, nd, rr, alive ? (aerial ? AERIAL : TUBE) : FILM);
                prev = nd;
            }
            double[] last = poly.get(poly.size() - 1);
            double rr = alive ? (Double.isFinite(last[4]) ? linkR(last[4], re) : re) : 1.2;
            if (st.bio().on() && alive && Double.isFinite(last[5])) {
                rr = last[4] * colarPt(Math.max(st.linkRMin(), re), last[5], last[6]);
            }
            addMeshEdge(prev, rb, rr, alive ? (last[3] > 0 ? AERIAL : TUBE) : FILM);
        }
        report.append(String.format(Locale.ROOT, " | mesh %d nodes %d edges %d foods %d live %d film", mm, ne, foods.size(),
                live, thin));
        return new int[]{linked, far};
    }

    void addMeshEdge(int a, int b, double r, byte k) {
        meshEdges.add(new int[]{a, b, k});
        meshRadius.add(new double[]{r});
    }

    int nearestMesh(int[] M, int u) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int j = 0; j < M.length; j++) {
            double d = sq(nX[M[j]] - nX[u], nY[M[j]] - nY[u], nZ[M[j]] - nZ[u]);
            if (d < bd) {
                bd = d;
                best = j;
            }
        }
        return best;
    }

    /** Tero et al. 2010: conductivity follows the flux between food pairs, drawn by vigor over 1 + distance / fluxL0. */
    void tero(int mm, int[] ea, int[] eb, double[] el, int[] comp, int c, List<Integer> foods, int nucM, double[] Dout,
                      SplittableRandom rnd) {
        double[] cum = null;
        int[] pa = null, pb = null;
        if (st.flux()) {
            int nf = foods.size(), np = nf * (nf - 1) / 2, k2 = 0;
            cum = new double[np];
            pa = new int[np];
            pb = new int[np];
            double tot = 0;
            for (int i = 0; i < nf; i++) {
                double[] fd = meshDist(mm, ea, eb, el, foods.get(i));
                for (int j = i + 1; j < nf; j++) {
                    double va = foods.get(i) == nucM ? 1 : st.vSub(), vb = foods.get(j) == nucM ? 1 : st.vSub();
                    double d = fd[foods.get(j)];
                    tot += Double.isFinite(d) ? va * vb / (1 + d / st.fluxL0()) : 0;
                    pa[k2] = foods.get(i);
                    pb[k2] = foods.get(j);
                    cum[k2++] = tot;
                }
            }
            for (int i = 0; i < np; i++) cum[i] /= tot;
        }
        List<Integer> es = new ArrayList<>();
        for (int e = 0; e < ea.length; e++) {
            if (comp[ea[e]] == c) es.add(e);
        }
        int k = es.size();
        double[] D = new double[k], L = new double[k];
        int[] a = new int[k], b = new int[k];
        for (int i = 0; i < k; i++) {
            int e = es.get(i);
            a[i] = ea[e];
            b[i] = eb[e];
            L[i] = el[e];
            D[i] = 1 + 0.01 * rnd.nextDouble();
        }
        double[] p = new double[mm], r = new double[mm], z = new double[mm], q = new double[mm], ap = new double[mm];
        double[] diag = new double[mm], gc = new double[k];
        long iters = 0, tSolve = System.nanoTime();
        int maxIt = 0, capped = 0;
        OrganicIchol ich = ic0 ? new OrganicIchol(mm, a, b, k) : null;
        for (int step = 0; step < st.tSteps(); step++) {
            int src, snk;
            if (cum != null) {
                double u = rnd.nextDouble();
                int lo = 0, hi = cum.length - 1;
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (cum[mid] < u) lo = mid + 1;
                    else hi = mid;
                }
                boolean flip = rnd.nextBoolean();
                src = flip ? pb[lo] : pa[lo];
                snk = flip ? pa[lo] : pb[lo];
            } else {
                src = foods.get(rnd.nextInt(foods.size()));
                do snk = foods.get(rnd.nextInt(foods.size())); while (snk == src);
            }
            Arrays.fill(diag, 0);
            for (int i = 0; i < k; i++) {
                gc[i] = Math.max(D[i], 1e-12) / L[i];
                diag[a[i]] += gc[i];
                diag[b[i]] += gc[i];
            }
            int used = 0;
            if (ich != null) {
                used = ich.solve(gc, diag, a, b, k, mm, src, snk, st.tI0(), p, r, z, q, ap);
            } else {
                // conjugate gradient on the Laplacian with the sink held at zero and I0 injected at the source
                Arrays.fill(p, 0);
                Arrays.fill(r, 0);
                r[src] = st.tI0();
                double rz = 0;
                for (int i = 0; i < mm; i++) {
                    z[i] = i == snk || diag[i] == 0 ? 0 : r[i] / diag[i];
                    q[i] = z[i];
                    rz += r[i] * z[i];
                }
                double r0 = Math.abs(st.tI0());
                for (int it = 0; it < 2000 && rz > 0; it++) {
                    used++;
                    Arrays.fill(ap, 0);
                    // q[snk] stays +0.0 (z[snk] is 0 and q = z + beta q), so the sink needs no test here.
                    for (int i = 0; i < k; i++) {
                        double f = gc[i] * (q[a[i]] - q[b[i]]);
                        ap[a[i]] += f;
                        ap[b[i]] -= f;
                    }
                    ap[snk] = 0;
                    double qap = 0;
                    for (int i = 0; i < mm; i++) qap += q[i] * ap[i];
                    if (qap <= 0) break;
                    double alpha = rz / qap, rn = 0, rz2 = 0;
                    for (int i = 0; i < mm; i++) {
                        if (i != snk) {
                            p[i] += alpha * q[i];
                            r[i] -= alpha * ap[i];
                            rn = Math.max(rn, Math.abs(r[i]));
                        }
                        z[i] = i == snk || diag[i] == 0 ? 0 : r[i] / diag[i];
                        rz2 += r[i] * z[i];
                    }
                    if (rn < 1e-9 * r0) break;
                    double beta = rz2 / rz;
                    rz = rz2;
                    for (int i = 0; i < mm; i++) q[i] = z[i] + beta * q[i];
                }
            }
            iters += used;
            maxIt = Math.max(maxIt, used);
            if (used == 2000) capped++;
            for (int i = 0; i < k; i++) {
                double fl = Math.abs(gc[i] * (p[a[i]] - p[b[i]])), fg = Math.pow(fl, st.tGamma());
                D[i] += st.tDt() * (fg / (1 + fg) - D[i]);
            }
        }
        double mx = 0;
        for (double d : D) mx = Math.max(mx, d);
        for (int i = 0; i < k; i++) Dout[es.get(i)] = mx > 0 ? D[i] / mx : 0;
        if (OrganicLab.TERO_STATS) {
            report.append(String.format(Locale.ROOT, " | tero %d steps %d edges, CG iterations %d (max %d, at cap %d), %s %d ms",
                    st.tSteps(), k, iters, maxIt, capped, ich != null ? "ic0 nodes " + ich.nc : "jacobi",
                    (System.nanoTime() - tSolve) / 1_000_000));
        }
    }

    /** Distances and parents from the nucleus over the live mesh edges. */
    double[][] meshTree(int mm, int[] ea, int[] eb, double[] D, int root, double[] el) {
        double[] d = new double[mm], par = new double[mm];
        Arrays.fill(d, Double.POSITIVE_INFINITY);
        Arrays.fill(par, -1);
        List<List<double[]>> adj = new ArrayList<>();
        for (int i = 0; i < mm; i++) adj.add(new ArrayList<>());
        for (int e = 0; e < ea.length; e++) {
            if (D[e] < st.tKeep()) continue;
            adj.get(ea[e]).add(new double[]{eb[e], el[e]});
            adj.get(eb[e]).add(new double[]{ea[e], el[e]});
        }
        OrganicHeap h = new OrganicHeap();
        d[root] = 0;
        h.push(0, root);
        while (h.size > 0) {
            double k = h.topKey();
            int u = h.pop();
            if (k > d[u]) continue;
            for (double[] e : adj.get(u)) {
                int v = (int) e[0];
                if (k + e[1] < d[v]) {
                    d[v] = k + e[1];
                    par[v] = u;
                    h.push(d[v], v);
                }
            }
        }
        return new double[][]{d, par};
    }

    /** Geodesic path of a mesh edge, oriented from the mesh node at its start, with aerial hops as ligament points. */
    List<double[]> polyline(Search geo, int u, int v, int startVox, double flowRadius, boolean record) {
        List<Integer> there = new ArrayList<>(), back = new ArrayList<>();
        for (int x = u; x >= 0; x = geo.pred[x]) there.add(x);
        for (int x = v; x >= 0; x = geo.pred[x]) back.add(x);
        java.util.Collections.reverse(there);
        List<Integer> cam = new ArrayList<>(there);
        cam.addAll(back);
        if (cam.get(0) != startVox) java.util.Collections.reverse(cam);
        List<double[]> out = new ArrayList<>();
        double[] c0 = center(cam.get(0));
        out.add(new double[]{c0[0], c0[1], c0[2], 0, Double.NaN, Double.NaN, 1});
        for (int i = 1; i < cam.size(); i++) {
            double[] a = center(cam.get(i - 1)), b = center(cam.get(i));
            double L = dist(a, b);
            if (L > 1.8) {
                double[] lo = a, hi = b;
                boolean swap = swapEnds(lo, hi);
                if (swap) {
                    lo = b;
                    hi = a;
                }
                boolean pillar = steep(lo, hi, st.linkAng());
                int steps = Math.max(2, (int) L);
                double[] fc = cargaF(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]);
                double perf = st.bio().comp() == 2 && pillar && L >= 40 ? Math.sqrt(1 - st.bio().compPhi()) : Double.NaN;
                for (int s = 1; s <= steps; s++) {
                    double t = swap ? 1 - (double) s / steps : (double) s / steps;
                    double[] p = s == steps ? b : linkPoint(lo, hi, t);
                    out.add(new double[]{p[0], p[1], p[2], pillar ? 2 : 1, Double.isNaN(perf) ? profile(pillar, t) : perf,
                            Math.min(t, 1 - t) * L, t < 0.5 ? fc[0] : fc[1]});
                }
                if (record) links.add(new double[]{pillar ? 1 : 0, lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], flowRadius});
            } else {
                out.add(new double[]{b[0], b[1], b[2], 0, Double.NaN, Double.NaN, 1});
            }
        }
        return out;
    }
}
