package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pruning the branches that lead nowhere. */
abstract class NetworkPruning extends NetworkFringe {
    NetworkPruning(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    void prune() {
        int[] kids = new int[m];
        for (int i = 0; i < m; i++) {
            if (pai[i] >= 0) kids[pai[i]]++;
        }
        boolean[] dead = new boolean[m];
        int pruned = 0;
        for (int i = 0; i < m; i++) {
            if (kids[i] != 0 || kind[i] != K_FRINGE || inMesh(i)) continue;
            List<Integer> chain = new ArrayList<>();
            double L = 0;
            int a = i;
            while (a >= 0 && kind[a] == K_FRINGE && kids[a] <= 1) {
                chain.add(a);
                int b = pai[a];
                if (b >= 0) L += Math.sqrt(sq(px[a] - px[b], py[a] - py[b], pz[a] - pz[b]));
                a = b;
            }
            double h = (mix((int) Math.floor(px[i]), (int) Math.floor(py[i]), (int) Math.floor(pz[i])) & 1023) / 1024.0;
            if (L < st.minTwig() && h >= st.keepTwig()) {
                for (int c : chain) dead[c] = true;
                pruned++;
            }
        }
        if (pruned == 0) return;
        int[] map = new int[m];
        int k = 0;
        for (int i = 0; i < m; i++) map[i] = dead[i] ? -1 : k++;
        for (int i = 0; i < m; i++) {
            if (dead[i]) continue;
            int j = map[i];
            px[j] = px[i];
            py[j] = py[i];
            pz[j] = pz[i];
            dn[j] = dn[i];
            peso[j] = peso[i];
            rf[j] = rf[i];
            cel[j] = cel[i];
            kind[j] = kind[i];
            via[j] = via[i];
            pai[j] = pai[i] >= 0 ? map[pai[i]] : -1;
        }
        for (int[] e : meshEdges) {
            e[0] = map[e[0]];
            e[1] = map[e[1]];
        }
        for (int[] fu : fusions) {
            for (int q = 0; q < 5; q++) {
                if (fu[q] >= 0) fu[q] = map[fu[q]];
            }
        }
        for (double[] oa : outAnchors) oa[0] = map[(int) oa[0]];
        noVox.replaceAll((key, v) -> map[v]);
        m = k;
        report.append(String.format(Locale.ROOT, " | pruned %d twigs, %d nodes", pruned, m));
    }

    boolean inMesh(int i) {
        return false;
    }
}
