package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Points hashed in cubic cells for the network's nearest-point queries. */
final class OrganicSpatialHash {
    final double size;
    final Map<Long, List<Integer>> cells = new HashMap<>();

    OrganicSpatialHash(double size) {
        this.size = size;
    }

    void add(int id, double x, double y, double z) {
        cells.computeIfAbsent(OrganicNetwork.key((int) Math.floor(x / size), (int) Math.floor(y / size), (int) Math.floor(z / size)),
                k -> new ArrayList<>()).add(id);
    }

    int nearest(double x, double y, double z, double r, java.util.function.IntFunction<double[]> pos) {
        int R = (int) Math.ceil(r / size);
        int cx = (int) Math.floor(x / size), cy = (int) Math.floor(y / size), cz = (int) Math.floor(z / size);
        int best = -1;
        double bd = r * r;
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    List<Integer> l = cells.get(OrganicNetwork.key(cx + dx, cy + dy, cz + dz));
                    if (l == null) continue;
                    for (int id : l) {
                        double[] p = pos.apply(id);
                        double d = OrganicNetwork.sq(p[0] - x, p[1] - y, p[2] - z);
                        if (d <= bd) {
                            bd = d;
                            best = id;
                        }
                    }
                }
            }
        }
        return best;
    }
}
