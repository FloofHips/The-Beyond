package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;

/** Radii from the flow, then the voxels stamped along the network. */
abstract class NetworkRaster extends NetworkPruning {
    NetworkRaster(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    /** Loops near food (Bebber 2007): two branches join through clear air where going round is loopK times the shortcut. */
    List<int[]> foodLoops() {
        Bio b = st.bio();
        List<int[]> out = new ArrayList<>();
        if (!b.foodLoops() || st.style() != Style.TREE) return out;
        List<Integer> foods = new ArrayList<>(), used = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            if (kind[i] == K_SUB || kind[i] == K_CORE) foods.add(i);
        }
        foods.sort((x, y) -> Double.compare(dn[x], dn[y]));
        int[] seen = new int[m];
        int gen = 0;
        double rr = b.loopR() * b.loopR(), dd = b.loopD() * b.loopD();
        for (int f : foods) {
            List<Integer> near = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                if (cel[i] != cel[f] || (kind[i] != K_TRUNK && kind[i] != K_SUB && kind[i] != K_FRINGE)) continue;
                if (sq(px[i] - px[f], py[i] - py[f], pz[i] - pz[f]) <= rr) near.add(i);
            }
            List<double[]> pairs = new ArrayList<>();
            for (int a = 0; a < near.size(); a++) {
                for (int c = a + 1; c < near.size(); c++) {
                    int i = near.get(a), j = near.get(c);
                    double d = sq(px[i] - px[j], py[i] - py[j], pz[i] - pz[j]);
                    if (d <= dd) pairs.add(new double[]{Math.sqrt(d), Math.min(i, j), Math.max(i, j)});
                }
            }
            pairs.sort((p, q) -> p[0] != q[0] ? Double.compare(p[0], q[0]) : p[1] != q[1] ? Double.compare(p[1], q[1])
                    : Double.compare(p[2], q[2]));
            int made = 0;
            for (double[] p : pairs) {
                if (made >= b.loopN()) break;
                int i = (int) p[1], j = (int) p[2];
                if (p[0] < 2 || nearUsed(used, i, j)) continue;
                double dt = treeDist(i, j, seen, ++gen);
                if (!Double.isFinite(dt) || dt < Math.max(8, b.loopK() * p[0]) || !clearLine(i, j)) continue;
                out.add(new int[]{i, j});
                used.add(i);
                used.add(j);
                made++;
            }
        }
        report.append(String.format(Locale.ROOT, " | food loops %d", out.size()));
        return out;
    }

    boolean nearUsed(List<Integer> used, int i, int j) {
        for (int u : used) {
            double di = sq(px[i] - px[u], py[i] - py[u], pz[i] - pz[u]), dj = sq(px[j] - px[u], py[j] - py[u], pz[j] - pz[u]);
            if (Math.min(di, dj) < 9) return true;
        }
        return false;
    }

    double treeDist(int i, int j, int[] seen, int stamp) {
        for (int a = i; a >= 0; a = pai[a]) seen[a] = stamp;
        int b = j;
        while (b >= 0 && seen[b] != stamp) b = pai[b];
        return b < 0 ? Double.POSITIVE_INFINITY : dn[i] + dn[j] - 2 * dn[b];
    }

    boolean clearLine(int i, int j) {
        int n = Math.max(2, (int) (2 * Math.sqrt(sq(px[j] - px[i], py[j] - py[i], pz[j] - pz[i]))));
        for (int s = 1; s < n; s++) {
            double t = (double) s / n;
            if (g.solidAt(px[i] + t * (px[j] - px[i]), py[i] + t * (py[j] - py[i]), pz[i] + t * (pz[j] - pz[i]))) return false;
        }
        return true;
    }

    Result raster() {
        List<int[]> loops = foodLoops();
        int[] kids = new int[m];
        for (int i = 0; i < m; i++) {
            if (pai[i] >= 0) kids[pai[i]]++;
        }
        double[] dem = new double[m], raio = new double[m];
        for (int i = 0; i < m; i++) dem[i] = peso[i] + (kids[i] == 0 ? 1 : 0);
        double[] dem0 = deadNodes == null ? null : dem.clone();
        Integer[] deep = new Integer[m];
        for (int i = 0; i < m; i++) deep[i] = i;
        Arrays.sort(deep, (a, b) -> Double.compare(dn[b], dn[a]));
        if (st.bio().on()) {
            fluxRadius5(dem, deep, raio);
        } else if (st.flux()) {
            fluxRadius(dem, deep, raio);
        } else {
            for (int i : deep) {
                if (pai[i] >= 0) dem[pai[i]] += dem[i];
            }
            for (int i = 0; i < m; i++) raio[i] = Math.max(st.rTip(), Math.min(st.rMax(), st.rTip() * Math.pow(dem[i], 1 / st.nExp())));
        }
        if (dem0 != null) atrophy(dem0, deep, raio);
        rFlow = raio.clone();
        for (int i = 0; i < m; i++) {
            if (!Double.isFinite(rf[i]) || (kind[i] != K_PILLAR && kind[i] != K_CABLE)) continue;
            if (st.flux()) raio[i] = Math.max(st.linkRMin(), raio[i] * rf[i]);
            else raio[i] = kind[i] == K_PILLAR ? Math.max(raio[i], rf[i]) : rf[i];
        }
        nodeR = raio.clone();
        int[] nCol = new int[3];
        if (st.bio().on()) {
            raio = collars(raio, nCol);
            if (st.bio().comp() == 2) figBundle(raio);
        }
        // chain smoothing, never into rock
        List<List<Integer>> kidsOf = new ArrayList<>();
        for (int i = 0; i < m; i++) kidsOf.add(new ArrayList<>());
        for (int i = 0; i < m; i++) {
            if (pai[i] >= 0) kidsOf.get(pai[i]).add(i);
        }
        for (int pass = 0; pass < 3; pass++) {
            double[] qx = px.clone(), qy = py.clone(), qz = pz.clone();
            for (int i = 0; i < m; i++) {
                if (pai[i] < 0 || kidsOf.get(i).size() != 1 || kind[i] == K_CORE || kind[i] == K_SUB) continue;
                int c = kidsOf.get(i).get(0), p = pai[i];
                double x = 0.5 * px[i] + 0.25 * (px[p] + px[c]), y = 0.5 * py[i] + 0.25 * (py[p] + py[c]),
                        z = 0.5 * pz[i] + 0.25 * (pz[p] + pz[c]);
                if (!g.solidAt(x, y, z)) {
                    qx[i] = x;
                    qy[i] = y;
                    qz[i] = z;
                }
            }
            px = qx;
            py = qy;
            pz = qz;
        }
        Stamp out = new Stamp();
        List<int[]> edges = new ArrayList<>();
        List<double[]> er = new ArrayList<>();
        if (st.style() == Style.MESH) {
            for (int i = 0; i < meshEdges.size(); i++) {
                int[] e = meshEdges.get(i);
                if (e[0] < 0 || e[1] < 0 || cut(e[0]) || cut(e[1])) continue;
                edges.add(e);
                double tube = meshRadius.get(i)[0];
                if (atrophyF != null) {
                    tube = Math.min(tube, st.rTip() + Math.max(0, tube - st.rTip()) * Math.min(atrophyF[e[0]], atrophyF[e[1]]));
                }
                er.add(new double[]{tube, tube});
            }
            for (int i = 0; i < m; i++) {
                if (pai[i] < 0 || cut(i)) continue;
                if (kind[i] == K_FRINGE) {
                    edges.add(new int[]{pai[i], i, TUBE});
                    er.add(new double[]{raio[pai[i]], raio[i]});
                } else if (via[i]) {
                    edges.add(new int[]{pai[i], i, kind[i] == K_PILLAR || kind[i] == K_CABLE ? AERIAL : CROSS});
                    er.add(new double[]{raio[pai[i]], raio[i]});
                }
            }
        } else {
            for (int i = 0; i < m; i++) {
                if (pai[i] < 0 || cut(i)) continue;
                byte k = kind[i] == K_PILLAR || kind[i] == K_CABLE ? AERIAL : kind[i] == K_CROSS ? CROSS : TUBE;
                edges.add(new int[]{pai[i], i, k});
                er.add(new double[]{raio[pai[i]], raio[i]});
            }
            for (int[] lp : loops) {
                if (cut(lp[0]) || cut(lp[1])) continue;
                double r = st.bio().loopF() * Math.min(raio[lp[0]], raio[lp[1]]);
                edges.add(new int[]{lp[1], lp[0], TUBE});
                er.add(new double[]{r, r});
            }
        }
        double[] rmesh = new double[m];
        if (st.style() == Style.MESH) {
            Arrays.fill(rmesh, st.rTip());
            for (int i = 0; i < edges.size(); i++) {
                int[] e = edges.get(i);
                if (e[2] == FILM) continue;
                rmesh[e[0]] = Math.max(rmesh[e[0]], er.get(i)[0]);
                rmesh[e[1]] = Math.max(rmesh[e[1]], er.get(i)[1]);
            }
            for (int i = 0; i < m; i++) nodeR[i] = via[i] || kind[i] == K_FRINGE ? nodeR[i] : rmesh[i];
            if (st.bio().on() && st.bio().meshBulge()) {
                int[] deg = new int[m];
                for (int[] e : edges) {
                    if (e[2] != TUBE) continue;
                    deg[e[0]]++;
                    deg[e[1]]++;
                }
                int bulges = 0;
                for (int i = 0; i < m; i++) {
                    if (deg[i] < 3) continue;
                    out.stamp(px[i], py[i], pz[i], st.bio().fj() * rmesh[i], dn[i], TUBE, false, false);
                    bulges++;
                }
                report.append(String.format(Locale.ROOT, " | mesh junctions swell %d", bulges));
            }
        }
        for (int i = 0; i < edges.size(); i++) {
            int[] e = edges.get(i);
            int a = e[0], b = e[1];
            double L = Math.sqrt(sq(px[b] - px[a], py[b] - py[a], pz[b] - pz[a]));
            int steps = Math.max(1, (int) (L / 0.5));
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                double x = px[a] + t * (px[b] - px[a]), y = py[a] + t * (py[b] - py[a]), z = pz[a] + t * (pz[b] - pz[a]);
                double r = er.get(i)[0] + t * (er.get(i)[1] - er.get(i)[0]), d = dn[a] + t * (dn[b] - dn[a]);
                if (e[2] == FILM) out.stamp(x, y, z, r, d, FILM, true, false);
                else if (st.pierce() > 0 && g.solidAt(x, y, z)) out.stamp(x, y, z, r, d, TUNNEL, false, true);
                else out.stamp(x, y, z, r, d, (byte) e[2], false, false);
            }
        }
        double[] rad = st.style() == Style.MESH ? rmesh : raio;
        int pend = pendants(out, rad);
        int[] fans = fans(out);
        for (int i = 0; i < m; i++) {
            if (kind[i] == K_CORE) organ(out, i, st.rCore());
            else if (kind[i] == K_SUB) organ(out, i, st.rSubBulb());
        }
        int film = 0;
        for (int i = 0; i < m; i++) {
            if (kids[i] != 0 || kind[i] != K_FRINGE) continue;
            List<Integer> c = new ArrayList<>();
            int a = i;
            while (a >= 0 && c.size() < 6 && kind[a] == K_FRINGE) {
                c.add(a);
                a = pai[a];
            }
            if (c.size() < 6) continue;
            for (int j = 0; j < 3; j++) out.stamp(px[c.get(j)], py[c.get(j)], pz[c.get(j)], st.film(), dn[c.get(j)], FILM, true, false);
            film++;
        }
        report.append(String.format(Locale.ROOT, " | network %d nodes, %d pendants, %d fans, %d companions, %d film tips,"
                + " %d ligaments", m, pend, fans[0], fans[1], film, links.size()));
        if (st.bio().on()) {
            report.append(String.format(Locale.ROOT, " | collars %d organs %d anchors %d forks, %d fusions", nCol[0], nCol[1],
                    nCol[2], fusions.size()));
        }
        float[] nodes = null;
        if (OrganicLab.keepNodes) {
            nodes = new float[m * 8];
            for (int i = 0; i < m; i++) {
                float[] v = {(float) px[i], (float) py[i], (float) pz[i], (float) rad[i], (float) dn[i], kind[i], pai[i], cel[i]};
                System.arraycopy(v, 0, nodes, i * 8, 8);
            }
        }
        float[] organs = organs();
        int[] owner = out.owners(organs);
        return out.result(nodes, owner, organs);
    }

    /** Thins roots above a dead organ to the flow they keep (Murray's law, foods alone on a mesh), dropping any under STARVED. */
    void atrophy(double[] dem, Integer[] deep, double[] raio) {
        boolean[] organ = new boolean[m], dead = new boolean[m];
        int organs = 0;
        for (int i : deadNodes) {
            if (i < 0 || i >= m || organ[i]) continue;
            organ[i] = true;
            organs++;
        }
        for (int k = deep.length - 1; k >= 0; k--) {
            int i = deep[k], p = pai[i];
            dead[i] = organ[i] || p >= 0 && dead[p] && cel[p] == cel[i] && kind[i] != K_CORE && kind[i] != K_SUB;
        }
        double[] all = new double[m], live = new double[m];
        boolean mesh = st.style() == Style.MESH;
        for (int i = 0; i < m; i++) {
            double far = distOut == null ? 0 : Math.max(distOut[i], 0);
            all[i] = mesh ? (kind[i] == K_SUB ? 1 : 0) : dem[i] / (1 + far / st.fluxL0());
            live[i] = dead[i] ? 0 : all[i];
        }
        for (int i : deep) {
            int p = pai[i];
            if (p < 0 || cel[p] != cel[i] || kind[i] == K_CORE) continue;
            all[p] += all[i];
            live[p] += live[i];
        }
        atrophyF = new double[m];
        starved = new boolean[m];
        int thin = 0, cut = 0;
        for (int i = 0; i < m; i++) {
            atrophyF[i] = 1;
            if (all[i] <= 0 || live[i] >= all[i]) continue;
            atrophyF[i] = Math.pow(live[i] / all[i], 1 / st.nExp());
            starved[i] = live[i] <= STARVED * all[i] && kind[i] != K_CORE;
            if (starved[i]) cut++;
            else thin++;
            raio[i] = Math.min(raio[i], st.rTip() + Math.max(0, raio[i] - st.rTip()) * atrophyF[i]);
        }
        report.append(String.format(Locale.ROOT, " | atrophy: %d dead organs, %d nodes thinner, %d dropped", organs, thin, cut));
    }

    boolean cut(int i) {
        return starved != null && starved[i];
    }

    /** Bulbs in node order, nuclei first, as ORGAN floats each (the flow share is the sub's flow over its nucleus's). */
    float[] organs() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            if (kind[i] == K_CORE) list.add(i);
        }
        for (int i = 0; i < m; i++) {
            if (kind[i] == K_SUB) list.add(i);
        }
        float[] out = new float[list.size() * ORGAN];
        for (int o = 0; o < list.size(); o++) {
            int i = list.get(o);
            boolean core = kind[i] == K_CORE;
            double share = core ? 1 : accOut != null && normOut[i] > 0 ? accOut[i] / normOut[i] : Double.NaN;
            float[] v = {(float) px[i], (float) py[i], (float) pz[i], (float) (core ? st.rCore() : st.rSubBulb()), core ? 0 : 1,
                    cel[i], (float) share, i};
            System.arraycopy(v, 0, out, o * ORGAN, ORGAN);
        }
        return out;
    }

    /** Flux radius: tips and subs weigh 1 / (1 + d / fluxL0) up to their nucleus, a cross-cell link the nuclei's exchange. */
    void fluxRadius(double[] dem, Integer[] deep, double[] raio) {
        Map<Integer, Integer> nuc = new HashMap<>();
        for (int i = 0; i < m; i++) {
            if (kind[i] == K_CORE) nuc.put(cel[i], i);
        }
        boolean[] cross = new boolean[m];
        Map<Integer, Double> apart = new HashMap<>();
        for (Map.Entry<Integer, Integer> en : nuc.entrySet()) {
            int c = en.getKey(), i = en.getValue(), a = pai[i], top = i;
            while (a >= 0 && cel[a] == c) {
                cross[a] = true;
                top = a;
                a = pai[a];
            }
            double from = a >= 0 ? (nuc.containsKey(cel[a]) ? dn[nuc.get(cel[a])] : dn[a])
                    : top != i && Double.isFinite(extCoreD) ? extCoreD : dn[top];
            apart.put(c, dn[i] - from);
        }
        Integer[] up = new Integer[m];
        for (int i = 0; i < m; i++) up[i] = i;
        Arrays.sort(up, (a, b) -> Double.compare(dn[a], dn[b]));
        int[] root = new int[m];
        double[] dist = new double[m], acc = new double[m];
        for (int i = 0; i < m; i++) root[i] = i;
        for (int i : up) {
            int p = pai[i];
            if (kind[i] == K_CORE || p < 0) continue;
            if (cross[i]) {
                root[i] = nuc.get(cel[i]);
                dist[i] = dn[root[i]] - dn[i];
            } else if (cel[p] == cel[i]) {
                root[i] = root[p];
                dist[i] = dist[p] + Math.sqrt(sq(px[i] - px[p], py[i] - py[p], pz[i] - pz[p]));
            }
        }
        for (int i = 0; i < m; i++) acc[i] = dem[i] / (1 + Math.max(dist[i], 0) / st.fluxL0());
        for (int i : deep) {
            int p = pai[i];
            if (p < 0 || cel[p] != cel[i]) continue;
            acc[p] += kind[i] == K_CORE ? acc[i] * st.fluxX() / (1 + Math.max(apart.get(cel[i]), 0) / st.fluxL0()) : acc[i];
        }
        for (int i = 0; i < m; i++) {
            int r = root[i];
            double share = (kind[r] == K_CORE ? 1 : st.vSub()) * acc[i] / Math.max(acc[r], 1e-9);
            raio[i] = st.rTip() + (st.rMax() - st.rTip()) * Math.pow(Math.min(1, share), 1 / st.nExp());
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Double> en : new TreeMap<>(apart).entrySet()) {
            if (en.getValue() > 0) sb.append(String.format(Locale.ROOT, " %d:%.0f", cores.get(en.getKey()).id(), en.getValue()));
        }
        if (sb.length() > 0) report.append(" | nuclei apart").append(sb);
    }

    /** Flux radius with the link capped at its anchor, thickness moving to old parts and cords thicker on poor ground. */
    void fluxRadius5(double[] dem, Integer[] deep, double[] raio) {
        Bio b = st.bio();
        Map<Integer, Integer> nuc = new TreeMap<>();
        for (int i = 0; i < m; i++) {
            if (kind[i] == K_CORE) nuc.put(cel[i], i);
        }
        boolean[] cross = new boolean[m];
        Map<Integer, Double> apart = new HashMap<>();
        Map<Integer, Integer> anchor = new TreeMap<>();
        Map<Integer, List<Integer>> path = new HashMap<>();
        java.util.Set<Integer> outside = new java.util.HashSet<>();
        for (Map.Entry<Integer, Integer> en : nuc.entrySet()) {
            int c = en.getKey(), i = en.getValue(), a = pai[i], top = i;
            List<Integer> cam = new ArrayList<>();
            while (a >= 0 && cel[a] == c) {
                cross[a] = true;
                cam.add(a);
                top = a;
                a = pai[a];
            }
            double from = a >= 0 ? (nuc.containsKey(cel[a]) ? dn[nuc.get(cel[a])] : dn[a])
                    : top != i && Double.isFinite(extCoreD) ? extCoreD : dn[top];
            apart.put(c, dn[i] - from);
            java.util.Collections.reverse(cam);
            if (a < 0 && top != i && kind[top] == K_CROSS) {
                // parent grown in another box: the trunk point the link leaves from is the anchor
                cam.remove(0);
                cross[top] = false;
                a = top;
                outside.add(c);
            }
            anchor.put(c, a);
            path.put(c, cam);
        }
        Integer[] up = new Integer[m];
        for (int i = 0; i < m; i++) up[i] = i;
        Arrays.sort(up, (x, y) -> Double.compare(dn[x], dn[y]));
        int[] root = new int[m];
        double[] dist = new double[m], acc = new double[m];
        for (int i = 0; i < m; i++) root[i] = i;
        for (double[] oa : outAnchors) {
            if (oa[0] >= 0 && Double.isFinite(oa[3])) dist[(int) oa[0]] = oa[3];
        }
        for (int i : up) {
            int p = pai[i];
            if (kind[i] == K_CORE || p < 0) continue;
            if (cel[p] == cel[i] || cross[i]) {
                root[i] = root[p];
                dist[i] = dist[p] + Math.sqrt(sq(px[i] - px[p], py[i] - py[p], pz[i] - pz[p]));
            }
        }
        boolean[] fuseStart = new boolean[m];
        for (int[] fu : fusions) fuseStart[fu[2] >= 0 ? fu[1] : fu[0]] = true;
        for (int i = 0; i < m; i++) acc[i] = dem[i] / (1 + Math.max(dist[i], 0) / st.fluxL0());
        for (int i : deep) {
            int p = pai[i];
            if (p < 0 || fuseStart[i] || cel[p] != cel[i]) continue;
            acc[p] += kind[i] == K_CORE ? acc[i] * st.fluxX() / (1 + Math.max(apart.get(cel[i]), 0) / st.fluxL0()) : acc[i];
        }
        List<Integer> cells = new ArrayList<>(anchor.keySet());
        cells.sort((x, y) -> Double.compare(dn[nuc.get(y)], dn[nuc.get(x)]));
        Map<Integer, double[]> outFlow = new HashMap<>();
        Map<Integer, Double> outQ = new HashMap<>();
        for (double[] oa : outAnchors) outFlow.put((int) oa[0], new double[]{oa[1], oa[2]});
        for (int c : cells) {
            int a = anchor.get(c);
            List<Integer> cam = path.get(c);
            if (a < 0 || cam.isEmpty()) continue;
            double[] of = outFlow.get(a);
            double want = acc[cam.get(0)];
            double q = outside.contains(c) ? (of != null && Double.isFinite(of[0]) ? Math.min(want, of[0]) : want)
                    : Math.min(want, acc[a]);
            for (int i : cam) acc[i] = q;
            if (outside.contains(c)) {
                outQ.put(a, q);
                int hang = 0;
                for (int i = 0; i < m; i++) {
                    if (root[i] == a && i != a && !cross[i] && dem[i] > 0) hang++;
                }
                report.append(String.format(Locale.ROOT, " | link out cell %d: demand %.3f cap %.3f q %.3f nucleus %.3f"
                        + " from %.1f, %d hanging", c, want, of != null ? of[0] : Double.NaN, q,
                        of != null ? of[1] : Double.NaN, dist[a], hang));
                continue;
            }
            for (int k = a; k >= 0 && cel[k] == cel[a]; k = pai[k]) acc[k] += q;
        }
        for (int[] fu : fusions) {
            int r = fu[0], y = fu[3];
            double lf = fu[2] >= 0 ? dist[fu[2]] : dist[y];
            double q = Math.min(acc[r] * (root[r] == r ? 1 / (1 + Math.max(lf, 0) / st.fluxL0()) : 1), acc[y]);
            if (fu[2] >= 0) {
                for (int i = fu[1]; i <= fu[2]; i++) acc[i] = q;
            }
            for (int k = y; k >= 0; k = pai[k]) {
                acc[k] += q;
                if (kind[k] == K_CORE) break;
            }
        }
        for (int i = 0; i < m; i++) {
            int r = root[i];
            double[] of = kind[r] == K_CROSS && pai[r] < 0 ? outFlow.get(r) : null;
            // the parent's nucleus flow also carries this link's exchange, as when both grow in one box
            double norm = of != null && Double.isFinite(of[1]) && of[1] > 0 ? of[1] + outQ.getOrDefault(r, 0.0) : acc[r];
            boolean full = kind[r] == K_CORE || (kind[r] == K_CROSS && pai[r] < 0);
            double share = (full ? 1 : st.vSub()) * acc[i] / Math.max(norm, 1e-9);
            raio[i] = st.rTip() + (st.rMax() - st.rTip()) * Math.pow(Math.min(1, share), 1 / st.nExp());
        }
        accOut = acc.clone();
        distOut = dist.clone();
        normOut = new double[m];
        for (int i = 0; i < m; i++) normOut[i] = acc[root[i]];
        double[] rTube = null;
        if (st.style() == Style.MESH) {
            rTube = new double[m];
            for (int k = 0; k < meshEdges.size(); k++) {
                int[] e = meshEdges.get(k);
                if (e[0] < 0 || e[1] < 0 || (e[2] != TUBE && e[2] != AERIAL)) continue;
                rTube[e[0]] = Math.max(rTube[e[0]], meshRadius.get(k)[0]);
                rTube[e[1]] = Math.max(rTube[e[1]], meshRadius.get(k)[0]);
            }
        }
        for (int c : cells) {
            int a = anchor.get(c);
            if (a < 0) continue;
            double ra = outside.contains(c) ? rf[a] : rTube != null && rTube[a] > 0 ? rTube[a] : raio[a];
            if (!Double.isFinite(ra)) continue;
            for (int i : path.get(c)) raio[i] = Math.min(raio[i], ra);
        }
        if (b.aAge() > 0) {
            Map<Integer, Double> dmax = new HashMap<>();
            for (int i = 0; i < m; i++) {
                if (!cross[i]) dmax.merge(root[i], dist[i], Math::max);
            }
            for (int i = 0; i < m; i++) {
                double dm = cross[i] ? 0 : dmax.getOrDefault(root[i], 0.0);
                if (dm > 0) raio[i] *= 1 + b.aAge() * (0.5 - dist[i] / dm);
            }
            for (int c : cells) {
                int a = anchor.get(c);
                List<Integer> cam = path.get(c);
                if (a < 0 || cam.isEmpty()) continue;
                double L = Math.max(dn[nuc.get(c)] - dn[a], 1e-6);
                Double fm = meet.get(c);
                if (fm == null) {
                    double v = Math.pow(raio[a] / st.rMax(), b.vFront());
                    fm = v / (v + 1);
                }
                double sm = fm * L;
                for (int i : cam) {
                    double sd = dn[i] - dn[a];
                    double age = sd <= sm ? (sm - sd) / Math.max(sm, 1e-6) : (sd - sm) / Math.max(L - sm, 1e-6);
                    raio[i] *= 1 + b.aAge() * (Math.max(0, Math.min(1, age)) - 0.5);
                }
            }
        }
        if (b.forage() && b.fCord() > 0) {
            for (int i = 0; i < m; i++) {
                if (kind[i] == K_TRUNK || kind[i] == K_CROSS || kind[i] == K_PILLAR || kind[i] == K_CABLE) {
                    raio[i] *= 1 + b.fCord() * (0.5 - rich(px[i], py[i], pz[i]));
                }
            }
        }
        int[] org = new int[m];
        Arrays.fill(org, -1);
        for (int i : up) org[i] = kind[i] == K_CORE || kind[i] == K_SUB ? i : pai[i] >= 0 ? org[pai[i]] : -1;
        for (int i = 0; i < m; i++) {
            raio[i] = Math.max(raio[i], st.rTip());
            if (kind[i] == K_CORE || kind[i] == K_SUB) {
                raio[i] = Math.min(raio[i], st.rMax());
                continue;
            }
            int o = pai[i] >= 0 ? org[pai[i]] : -1;
            if (o >= 0) raio[i] = Math.min(raio[i], b.kb() * (kind[o] == K_CORE ? st.rCore() : st.rSubBulb()));
        }
        for (int[] fu : fusions) {
            if (fu[2] < 0) continue;
            double ry = rTube != null && rTube[fu[3]] > 0 ? rTube[fu[3]] : raio[fu[3]];
            for (int i = fu[1]; i <= fu[2]; i++) raio[i] = Math.min(raio[i], Math.min(ry, raio[fu[4]]));
        }
        for (int c : outside) {
            int a = anchor.get(c);
            if (Double.isFinite(rf[a])) raio[a] = rf[a];
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Double> en : new TreeMap<>(apart).entrySet()) {
            if (en.getValue() > 0) sb.append(String.format(Locale.ROOT, " %d:%.0f", cores.get(en.getKey()).id(), en.getValue()));
        }
        if (sb.length() > 0) report.append(" | nuclei apart").append(sb);
    }

    /** Junction collars: a root swells to fj of the thickest root there, by ko to kc of a bulb, fading over zj radii. */
    double[] collars(double[] raio, int[] nCol) {
        Bio b = st.bio();
        List<List<Integer>> kidsOf = new ArrayList<>();
        for (int i = 0; i < m; i++) kidsOf.add(new ArrayList<>());
        for (int i = 0; i < m; i++) {
            if (pai[i] >= 0) kidsOf.get(pai[i]).add(i);
        }
        boolean[] link = new boolean[m];
        for (int i = 0; i < m; i++) link[i] = kind[i] == K_PILLAR || kind[i] == K_CABLE;
        Map<Integer, Double> load = new HashMap<>();
        if (b.load() > 0) {
            for (int j0 = 0; j0 < m; j0++) {
                if (!link[j0] || (pai[j0] >= 0 && link[pai[j0]])) continue;
                int last = j0;
                while (true) {
                    int next = -1, count = 0;
                    for (int x : kidsOf.get(last)) {
                        if (link[x]) {
                            next = x;
                            count++;
                        }
                    }
                    if (count != 1) break;
                    last = next;
                }
                double[] fc = cargaF(px[last] - px[j0], py[last] - py[j0], pz[last] - pz[j0]);
                double f0 = py[j0] >= py[last] ? fc[0] : fc[1], f1 = py[j0] >= py[last] ? fc[1] : fc[0];
                load.merge(j0, f0, Math::max);
                if (pai[j0] >= 0) load.merge(pai[j0], f0, Math::max);
                load.merge(last, f1, Math::max);
                for (int x : kidsOf.get(last)) {
                    if (!link[x]) load.merge(x, f1, Math::max);
                }
            }
        }
        double[] base = raio.clone(), out = raio.clone();
        for (int j = 0; j < m; j++) {
            List<Integer> fs = kidsOf.get(j);
            boolean organ = kind[j] == K_CORE || kind[j] == K_SUB;
            boolean anc = pai[j] >= 0 && link[pai[j]] != link[j];
            for (int x : fs) anc |= link[x] != link[j];
            if (!(organ || anc || fs.size() >= 2)) continue;
            double rm = base[j];
            for (int x : fs) rm = Math.max(rm, base[x]);
            double rc, s0;
            if (organ) {
                double rb = kind[j] == K_CORE ? st.rCore() : st.rSubBulb();
                rc = Math.min(b.kc() * rb, Math.max(b.fj() * rm, b.ko() * rb));
                s0 = rb;
                nCol[0]++;
            } else {
                rc = (anc ? b.fjLink() * load.getOrDefault(j, 1.0) : b.fj()) * rm;
                s0 = 0;
                nCol[anc ? 1 : 2]++;
            }
            double lam = s0 + b.zj() * rc;
            List<double[]> stack = new ArrayList<>();
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            stack.add(new double[]{j, 0});
            seen.add(j);
            while (!stack.isEmpty()) {
                double[] top = stack.remove(stack.size() - 1);
                int a = (int) top[0];
                double sd = top[1];
                double w = sd <= s0 ? 1 : 0.5 * (1 + Math.cos(Math.PI * Math.min(1, (sd - s0) / Math.max(lam - s0, 1e-6))));
                if (rc > base[a]) out[a] = Math.max(out[a], base[a] + (rc - base[a]) * w);
                List<Integer> nb = new ArrayList<>(kidsOf.get(a));
                if (pai[a] >= 0) nb.add(pai[a]);
                for (int c : nb) {
                    if (seen.contains(c)) continue;
                    double s2 = sd + Math.sqrt(sq(px[c] - px[a], py[c] - py[a], pz[c] - pz[a]));
                    if (s2 < lam) {
                        seen.add(c);
                        stack.add(new double[]{c, s2});
                    }
                }
            }
        }
        return out;
    }

    /** Fig bundle: a long pillar chain shares its cross-section with the twisted companion (summed area of one). */
    void figBundle(double[] raio) {
        List<List<Integer>> kidsOf = new ArrayList<>();
        for (int i = 0; i < m; i++) kidsOf.add(new ArrayList<>());
        for (int i = 0; i < m; i++) {
            if (pai[i] >= 0) kidsOf.get(pai[i]).add(i);
        }
        double f = Math.sqrt(1 - st.bio().compPhi());
        for (int i = 0; i < m; i++) {
            if (kind[i] != K_PILLAR || (pai[i] >= 0 && kind[pai[i]] == K_PILLAR)) continue;
            List<Integer> chain = new ArrayList<>();
            int a = i;
            double len = 0;
            while (true) {
                chain.add(a);
                int next = -1, count = 0;
                for (int x : kidsOf.get(a)) {
                    if (kind[x] == K_PILLAR) {
                        next = x;
                        count++;
                    }
                }
                if (count != 1) break;
                len += Math.sqrt(sq(px[next] - px[a], py[next] - py[a], pz[next] - pz[a]));
                a = next;
            }
            if (chain.size() > 1 && len >= 40) {
                for (int c : chain) raio[c] *= f;
            }
        }
    }

    /** Fig companion: an aerial root wound around the pillar, leaving and arriving beside it, sharing its cross-section. */
    boolean figHelix(Stamp out, double[] a, double[] b, double len, double rb, double ps, double d0) {
        double rm = Math.sqrt(1 - st.bio().compPhi()) * linkR(1.0, rb);
        double rc = Math.max(st.rTip(), Math.sqrt(st.bio().compPhi()) * linkR(1.0, rb));
        double turns = Math.max(1.0, len / 35.0);
        List<double[]> am = new ArrayList<>();
        int steps = (int) (len * 2);
        for (int s = 0; s <= steps; s++) {
            double t = s / (len * 2);
            double[] c0 = linkPoint(a, b, t), c1 = t < 0.98 ? linkPoint(a, b, Math.min(1, t + 0.02)) : linkPoint(a, b, t - 0.02);
            double tx = c1[0] - c0[0], ty = c1[1] - c0[1], tz = c1[2] - c0[2];
            if (t >= 0.98) {
                tx = -tx;
                ty = -ty;
                tz = -tz;
            }
            double tl = Math.max(Math.sqrt(tx * tx + ty * ty + tz * tz), 1e-9);
            tx /= tl;
            ty /= tl;
            tz /= tl;
            double ux = 0, uy = tz, uz = -ty;
            if (Math.sqrt(uy * uy + uz * uz) < 0.3) {
                ux = ty;
                uy = -tx;
                uz = 0;
            }
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul;
            uy /= ul;
            uz /= ul;
            double vx = ty * uz - tz * uy, vy = tz * ux - tx * uz, vz = tx * uy - ty * ux;
            double ang = ps + 2 * Math.PI * turns * t, rh = (rm + rc - 0.3) * Math.min(1, 4 * Math.sin(Math.PI * t));
            double qx = c0[0] + rh * (Math.cos(ang) * ux + Math.sin(ang) * vx), qy = c0[1] + rh * (Math.cos(ang) * uy
                    + Math.sin(ang) * vy), qz = c0[2] + rh * (Math.cos(ang) * uz + Math.sin(ang) * vz);
            boolean rock = g.solidAt(qx, qy, qz);
            if (rock && (5 < t * len && t * len < len - 5 || st.pierce() <= 0)) return false;
            am.add(new double[]{qx, qy, qz, rock ? 1 : 0, t * len});
        }
        for (double[] p : am) out.stamp(p[0], p[1], p[2], rc, d0 + p[4], p[3] > 0 ? TUNNEL : AERIAL, false, p[3] > 0);
        return true;
    }

    int pendants(Stamp out, double[] rad) {
        if (st.pendEvery() <= 0) return 0;
        int count = 0;
        for (int i = 0; i < m; i++) {
            if (rad[i] < st.pendR() || (kind[i] != K_TRUNK && kind[i] != K_CROSS && kind[i] != K_SUB)) continue;
            int h = mix((int) Math.floor(px[i] * 3), (int) Math.floor(py[i] * 3), (int) Math.floor(pz[i] * 3)) & 0x7FFFFFFF;
            if (h % st.pendEvery() != 0) continue;
            double u = (h / 7 % 1000) / 1000.0;
            double len = st.pendMin() + (st.pendMax() - st.pendMin()) * u * Math.min(1, rad[i] / st.rMax() + 0.3);
            double ang = (h / 7919 % 628) / 100.0;
            double tx = px[i], ty = py[i] - rad[i] * 0.8, tz = pz[i];
            List<double[]> samples = new ArrayList<>();
            for (int s = 1; s <= (int) (len / 0.5); s++) {
                double t = s * 0.5 / len;
                double x = tx + 0.6 * Math.sin(Math.PI * t) * Math.cos(ang), y = ty - s * 0.5,
                        z = tz + 0.6 * Math.sin(Math.PI * t) * Math.sin(ang);
                int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
                if (!g.inside(ix, iy, iz)) break;
                int vi = g.index(ix, iy, iz);
                if (g.solid(vi) || !g.bog(vi)) break;
                samples.add(new double[]{x, y, z, Math.max(st.rTip(), 0.6 * rad[i] * (1 - t))});
            }
            if (samples.size() < 4) continue;
            count++;
            for (double[] p : samples) out.stamp(p[0], p[1], p[2], p[3], dn[i], PENDANT, false, false);
        }
        return count;
    }

    /** Support roots at both ends of every ligament, and a companion strand beside long pillars that merges midway. */
    int[] fans(Stamp out) {
        Map<String, double[]> unique = new TreeMap<>();
        for (double[] l : links) {
            unique.putIfAbsent(String.format(Locale.ROOT, "%.0f,%.0f,%.0f,%.0f,%.0f,%.0f", Math.floor(l[1]), Math.floor(l[2]),
                    Math.floor(l[3]), Math.floor(l[4]), Math.floor(l[5]), Math.floor(l[6])), l);
        }
        if (unique.isEmpty()) return new int[]{0, 0};
        OrganicSpatialHash surf = new OrganicSpatialHash(2);
        for (int u = 0; u < n; u++) {
            if ((nFlag[u] & F_ROCK) == 0 && nDist[u] <= 1.5) surf.add(u, nX[u] + 0.5, nY[u] + 0.5, nZ[u] + 0.5);
        }
        OrganicSpatialHash net = new OrganicSpatialHash(4);
        for (int i = 0; i < m; i++) net.add(i, px[i], py[i], pz[i]);
        int fans = 0, companions = 0;
        for (Map.Entry<String, double[]> en : unique.entrySet()) {
            double[] l = en.getValue();
            boolean pillar = l[0] > 0;
            double[] a = {l[1], l[2], l[3]}, b = {l[4], l[5], l[6]};
            double rb = l[7];
            if (st.flux() && !Double.isFinite(rb)) {
                double[] mid = linkPoint(a, b, 0.5);
                int near = net.nearest(mid[0], mid[1], mid[2], 8, this::nodePos);
                rb = near >= 0 ? rFlow[near] : st.rTip();
            }
            SplittableRandom rnd = new SplittableRandom(st.seed() ^ en.getKey().hashCode());
            double[][] ends = {a, b};
            for (int e = 0; e < 2; e++) {
                double[] p0 = ends[e];
                int nf = pillar ? (e == 0 ? 3 : 4) : 2;
                double r0 = pillar ? (e == 0 ? 0.8 * linkR(profile(true, 0), rb) : 0.75 * linkR(profile(true, 1), rb))
                        : 0.8 * (st.flux() ? linkR(profile(false, 0), rb) : st.linkRe());
                double lmin = pillar ? (e == 0 ? 5 : 6) : 4, lmax = pillar ? (e == 0 ? 8 : 10) : 7;
                if (st.flux()) {
                    double f = Math.max(0.6, Math.min(1.3, r0 / 1.6));
                    lmin *= f;
                    lmax *= f;
                }
                double baseAng;
                if (pillar) {
                    baseAng = rnd.nextDouble() * 2 * Math.PI;
                } else {
                    double hx = b[0] - a[0], hz = b[2] - a[2];
                    baseAng = e == 0 ? Math.atan2(-hz, -hx) : Math.atan2(hz, hx);
                }
                int near = net.nearest(p0[0], p0[1], p0[2], 6, this::nodePos);
                double d0 = near >= 0 ? dn[near] : 0;
                for (int f = 0; f < nf; f++) {
                    double ang = pillar ? baseAng + 2 * Math.PI * f / nf + rnd.nextGaussian() * 0.25
                            : baseAng + (f - (nf - 1) / 2.0) + rnd.nextGaussian() * 0.2;
                    double len = lmin + (lmax - lmin) * rnd.nextDouble();
                    double[] pos = p0.clone();
                    double s = 0;
                    List<double[]> am = new ArrayList<>();
                    while (s < len) {
                        double tx = pos[0] + 0.8 * Math.cos(ang), ty = pos[1], tz = pos[2] + 0.8 * Math.sin(ang);
                        int h = surf.nearest(tx, ty, tz, 1.3, this::graphPos);
                        if (h < 0) break;
                        double[] q = graphPos(h);
                        double step = dist(q, pos);
                        if (step < 0.5) {
                            pos = new double[]{tx, ty, tz};
                            s += 0.8;
                            continue;
                        }
                        s += step;
                        double r = Math.max(st.rTip(), r0 * (1 - s / len));
                        am.add(new double[]{(q[0] + pos[0]) / 2, (q[1] + pos[1]) / 2, (q[2] + pos[2]) / 2, r, d0 + s});
                        am.add(new double[]{q[0], q[1], q[2], r, d0 + s});
                        pos = q;
                        ang += rnd.nextGaussian() * 0.12;
                    }
                    if (am.size() < 4) continue;
                    fans++;
                    for (double[] p : am) out.stamp(p[0], p[1], p[2], p[3], p[4], TUBE, false, false);
                }
            }
            double len = dist(a, b);
            if (!pillar || len < 40 || (st.bio().on() && st.bio().comp() == 0)) continue;
            double ps = rnd.nextDouble() * 2 * Math.PI;
            if (st.bio().on() && st.bio().comp() == 2) {
                int near = net.nearest(a[0], a[1], a[2], 6, this::nodePos);
                if (figHelix(out, a, b, len, rb, ps, near >= 0 ? dn[near] : 0)) companions++;
                continue;
            }
            double[] e1 = {Math.cos(ps), 0, Math.sin(ps)}, e2 = {Math.cos(ps + 0.6), 0, Math.sin(ps + 0.6)};
            int ha = surf.nearest(a[0] + 3.5 * e1[0], a[1], a[2] + 3.5 * e1[2], 3, this::graphPos);
            int hb = surf.nearest(b[0] + 4.5 * e2[0], b[1], b[2] + 4.5 * e2[2], 3, this::graphPos);
            if (ha < 0 || hb < 0) continue;
            double[] oa = graphPos(ha), ob = graphPos(hb);
            for (int k = 0; k < 3; k++) {
                oa[k] -= a[k];
                ob[k] -= b[k];
            }
            double tm = 0.4 + 0.25 * rnd.nextDouble();
            int near = net.nearest(a[0], a[1], a[2], 6, this::nodePos);
            double d0 = near >= 0 ? dn[near] : 0;
            List<double[]> am = new ArrayList<>();
            boolean ok = true;
            for (int s = 0; s <= (int) (len * 2); s++) {
                double t = s / (len * 2), w = 1 - Math.exp(-Math.pow((t - tm) / 0.09, 2));
                double[] q = linkPoint(a, b, t);
                double sway = w * 0.8 * Math.sin(3 * Math.PI * t + ps);
                for (int k = 0; k < 3; k++) q[k] += w * ((1 - t) * oa[k] + t * ob[k]) + sway * e1[k];
                boolean rock = g.solidAt(q[0], q[1], q[2]);
                if (rock && (5 < t * len && t * len < len - 5 || st.pierce() <= 0)) {
                    ok = false;
                    break;
                }
                am.add(new double[]{q[0], q[1], q[2], Math.max(st.rTip(), 0.55 * linkR(profile(true, t), rb)), d0 + t * len,
                        rock ? 1 : 0});
            }
            if (!ok) continue;
            companions++;
            for (double[] p : am) out.stamp(p[0], p[1], p[2], p[3], p[4], p[5] > 0 ? TUNNEL : AERIAL, false, p[5] > 0);
        }
        return new int[]{fans, companions};
    }

    // a bulb on a floor with room above rises as a dome or a stalked head, so it stands out of the tissue mat around it
    private void organ(Stamp out, int i, double r) {
        int x = (int) Math.floor(px[i]), y = (int) Math.floor(py[i]), z = (int) Math.floor(pz[i]);
        int base = g.solidAt(x, y - 1, z) ? y : g.solidAt(x, y - 2, z) ? y - 1 : Integer.MIN_VALUE;
        double h = st.bulgeH() * r;
        boolean room = st.bulge() != 0 && base != Integer.MIN_VALUE;
        for (int k = 0; room && k <= (int) Math.ceil(h + r); k++) room = !g.solidAt(x, base + k, z);
        if (!room) out.stamp(px[i], py[i], pz[i], r, dn[i], BULB, false, false);
        else if (st.bulge() == 1) out.dome(px[i], base, pz[i], r, h, dn[i]);
        else out.stalk(px[i], base, pz[i], r, h, dn[i]);
    }

    /** Voxel writer: spheres per sample, nearest network distance kept, the higher kind wins (film never overwrites). */
    final class Stamp {
        final OrganicIntIntMap slot = new OrganicIntIntMap(1 << 14);
        int count;
        int[] vox = new int[1024];
        byte[] kinds = new byte[1024];
        float[] dist = new float[1024];
        byte[] thick = new byte[1024];
        final Map<Integer, int[][]> spheres = new HashMap<>();

        void stamp(double x, double y, double z, double r, double d, byte k, boolean surfaceOnly, boolean rock) {
            int rq = (int) Math.round(r * 2);
            int[][] off = spheres.computeIfAbsent(rq, q -> sphere(q / 2.0));
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
            for (int[] o : off) {
                int vx = cx + o[0], vy = cy + o[1], vz = cz + o[2];
                if (!g.inside(vx, vy, vz)) continue;
                int i = g.index(vx, vy, vz);
                if (g.solid(i) && !(rock && g.bog(i))) continue;
                if (surfaceOnly) {
                    int u = nodeAt(i);
                    if (u < 0 || nDist[u] > 1.5 || (nFlag[u] & F_ROCK) != 0) continue;
                }
                int s = slot.get(i);
                if (s < 0) {
                    if (count == vox.length) {
                        vox = Arrays.copyOf(vox, count * 2);
                        kinds = Arrays.copyOf(kinds, count * 2);
                        dist = Arrays.copyOf(dist, count * 2);
                        thick = Arrays.copyOf(thick, count * 2);
                    }
                    s = count++;
                    slot.put(i, s);
                    vox[s] = i;
                    kinds[s] = k;
                    dist[s] = (float) d;
                    thick[s] = (byte) Math.min(127, rq);
                } else {
                    dist[s] = Math.min(dist[s], (float) d);
                    if (rq > thick[s]) thick[s] = (byte) Math.min(127, rq);
                    if (k != FILM) kinds[s] = (byte) Math.max(kinds[s], k);
                }
            }
        }

        /** Half ellipsoid standing on the floor at base: radius r across, h tall. */
        void dome(double x, int base, double z, double r, double h, double d) {
            int rq = (int) Math.round(r * 2), R = (int) Math.ceil(r), H = (int) Math.ceil(h);
            int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
            for (int a = -R; a <= R; a++) {
                for (int b = 0; b <= H; b++) {
                    for (int c = -R; c <= R; c++) {
                        double ex = (cx + a + 0.5 - x) / r, ey = (b + 0.5) / h, ez = (cz + c + 0.5 - z) / r;
                        if (ex * ex + ey * ey + ez * ez <= 1) put(cx + a, base + b, cz + c, d, rq);
                    }
                }
            }
        }

        /** A head of radius r on a stalk, h tall to the head's middle, like a sporangium. */
        void stalk(double x, int base, double z, double r, double h, double d) {
            int rq = (int) Math.round(r * 2), cx = (int) Math.floor(x), cz = (int) Math.floor(z);
            double rs = Math.max(0.75, 0.35 * r);
            int S = (int) Math.ceil(rs);
            for (int b = 0; b <= (int) Math.ceil(h); b++) {
                for (int a = -S; a <= S; a++) {
                    for (int c = -S; c <= S; c++) {
                        double ex = cx + a + 0.5 - x, ez = cz + c + 0.5 - z;
                        if (ex * ex + ez * ez <= rs * rs) put(cx + a, base + b, cz + c, d, rq);
                    }
                }
            }
            int[][] off = spheres.computeIfAbsent(rq, q -> sphere(q / 2.0));
            int cy = base + (int) Math.round(h);
            for (int[] o : off) put(cx + o[0], cy + o[1], cz + o[2], d, rq);
        }

        private void put(int vx, int vy, int vz, double d, int rq) {
            if (!g.inside(vx, vy, vz)) return;
            int i = g.index(vx, vy, vz);
            if (g.solid(i)) return;
            int s = slot.get(i);
            if (s < 0) {
                if (count == vox.length) {
                    vox = Arrays.copyOf(vox, count * 2);
                    kinds = Arrays.copyOf(kinds, count * 2);
                    dist = Arrays.copyOf(dist, count * 2);
                    thick = Arrays.copyOf(thick, count * 2);
                }
                s = count++;
                slot.put(i, s);
                vox[s] = i;
                kinds[s] = BULB;
                dist[s] = (float) d;
                thick[s] = (byte) Math.min(127, rq);
            } else {
                dist[s] = Math.min(dist[s], (float) d);
                if (rq > thick[s]) thick[s] = (byte) Math.min(127, rq);
                kinds[s] = (byte) Math.max(kinds[s], BULB);
            }
        }

        Result result(float[] nodes, int[] owner, float[] organs) {
            return new Result(Arrays.copyOf(vox, count), Arrays.copyOf(kinds, count), Arrays.copyOf(dist, count), null, null, null,
                    null, null, null, "", nodes, false, owner, organs, Arrays.copyOf(thick, count));
        }

        /** Nearest bulb through the stamped tissue, or the nearest centre for tissue no bulb reaches through it. */
        int[] owners(float[] organs) {
            int n = organs.length / ORGAN;
            int[] own = new int[count];
            Arrays.fill(own, -1);
            if (n == 0) return own;
            double[] best = new double[count];
            Arrays.fill(best, Double.POSITIVE_INFINITY);
            int[] bulbs = new int[n];
            for (int o = 0; o < n; o++) {
                int node = (int) organs[o * ORGAN + 7];
                double x = px[node], y = py[node], z = pz[node], r = kind[node] == K_CORE ? st.rCore() : st.rSubBulb();
                int[][] off = spheres.computeIfAbsent((int) Math.round(r * 2), q -> sphere(q / 2.0));
                int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
                for (int[] f : off) {
                    int vx = cx + f[0], vy = cy + f[1], vz = cz + f[2];
                    if (!g.inside(vx, vy, vz)) continue;
                    int s = slot.get(g.index(vx, vy, vz));
                    if (s < 0 || kinds[s] != BULB) continue;
                    double d2 = sq(vx + 0.5 - x, vy + 0.5 - y, vz + 0.5 - z);
                    if (d2 < best[s] || (d2 == best[s] && o < own[s])) {
                        best[s] = d2;
                        own[s] = o;
                    }
                }
            }
            double[] dist = new double[count];
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            OrganicHeap h = new OrganicHeap();
            for (int s = 0; s < count; s++) {
                if (own[s] < 0) continue;
                bulbs[own[s]]++;
                dist[s] = 0;
                h.push(0, s);
            }
            int plane = g.ny * g.nz;
            while (h.size > 0) {
                double k = h.topKey();
                int s = h.pop();
                if (k > dist[s]) continue;
                int i = vox[s], x = i / plane, y = (i / g.nz) % g.ny, z = i % g.nz;
                for (int e = 0; e < 26; e++) {
                    int vx = x + OFF26[e][0], vy = y + OFF26[e][1], vz = z + OFF26[e][2];
                    if (!g.inside(vx, vy, vz)) continue;
                    int t = slot.get(g.index(vx, vy, vz));
                    if (t < 0) continue;
                    double nd = dist[s] + LEN26[e];
                    if (nd < dist[t] || (nd == dist[t] && own[s] < own[t])) {
                        dist[t] = nd;
                        own[t] = own[s];
                        h.push(nd, t);
                    }
                }
            }
            int orphans = 0;
            for (int s = 0; s < count; s++) {
                if (own[s] >= 0) continue;
                orphans++;
                int i = vox[s];
                double x = i / plane + 0.5, y = (i / g.nz) % g.ny + 0.5, z = i % g.nz + 0.5, near = Double.POSITIVE_INFINITY;
                for (int o = 0; o < n; o++) {
                    double d2 = sq(x - organs[o * ORGAN], y - organs[o * ORGAN + 1], z - organs[o * ORGAN + 2]);
                    if (d2 < near) {
                        near = d2;
                        own[s] = o;
                    }
                }
            }
            int empty = 0;
            for (int b : bulbs) {
                if (b == 0) empty++;
            }
            report.append(String.format(Locale.ROOT, " | owners %d organs, %d without bulb voxels, %d orphan voxels", n, empty,
                    orphans));
            return own;
        }
    }
}
