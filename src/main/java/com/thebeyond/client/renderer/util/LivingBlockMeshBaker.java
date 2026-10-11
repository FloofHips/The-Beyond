package com.thebeyond.client.renderer.util;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LivingBlockMeshBaker {

    public static final int TEXELS_PER_BLOCK = 16;
    public static final int TEXTURE_BLOCKS = 1;
    public static final double BORDER_SOURCE_TEXELS = 2.0;
    public static final double BORDER_WORLD_TEXELS = 2.0;

    private static final int CTM_GRID = 4;
    private static final int CTM_INTERIOR_TILES = 2;
    private static final double INTERIOR_UV_SPAN = (double) CTM_INTERIOR_TILES / CTM_GRID;

    private static final double TILE = 1.0 / TEXTURE_BLOCKS;
    private static final double BORDER_UV = BORDER_SOURCE_TEXELS / TEXELS_PER_BLOCK;
    private static final double BORDER_WIDTH = BORDER_WORLD_TEXELS / TEXELS_PER_BLOCK;
    private static final double TOUCH = 1.0E-6;
    private static final double PROBE_DEPTH = 1.0E-4;
    private static final double PLANE_SNAP = 1.0E-5;
    private static final double MERGE_SNAP = 1.0E-5;

    public record MeshQuad(float[] xyz, float[] uv, float nx, float ny, float nz, boolean rim) { }

    private LivingBlockMeshBaker() {
    }

    public static List<MeshQuad> bake(final List<AABB> boxes) {
        List<MeshQuad> out = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            bakeDirection(boxes, dir, out);
        }
        return out;
    }

    private static void bakeDirection(final List<AABB> boxes, final Direction dir, final List<MeshQuad> out) {
        Map<Long, List<AABB>> planes = new HashMap<>();
        for (AABB box : boxes) {
            double depth = FaceFrame.depthOf(box, dir);
            planes.computeIfAbsent(Math.round(depth / PLANE_SNAP), key -> new ArrayList<>()).add(box);
        }
        for (Map.Entry<Long, List<AABB>> plane : planes.entrySet()) {
            bakePlane(boxes, plane.getValue(), new FaceFrame(dir, plane.getKey() * PLANE_SNAP), out);
        }
    }

    private static void bakePlane(final List<AABB> allBoxes, final List<AABB> owners,
                                  final FaceFrame frame, final List<MeshQuad> out) {
        List<Rect> visible = new ArrayList<>();
        for (AABB box : owners) {
            visible.add(frame.project(box));
        }

        List<Rect> occluders = new ArrayList<>();
        for (AABB box : allBoxes) {
            if (frame.blocksOutside(box)) {
                occluders.add(frame.project(box));
            }
        }

        double[] us = gridAxis(visible, occluders, true);
        double[] vs = gridAxis(visible, occluders, false);
        if (us.length < 2 || vs.length < 2) {
            return;
        }

        int columns = us.length - 1;
        int rows = vs.length - 1;
        Patch[] patches = new Patch[columns * rows];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                double u = (us[col] + us[col + 1]) * 0.5;
                double v = (vs[row] + vs[row + 1]) * 0.5;
                patches[row * columns + col] = classify(allBoxes, visible, occluders, frame, u, v);
            }
        }

        emitMerged(out, frame, us, vs, patches, columns, rows,
                tileIndices(us, columns), tileIndices(vs, rows));
    }

    private static Patch classify(final List<AABB> allBoxes, final List<Rect> visible, final List<Rect> occluders,
                                  final FaceFrame frame, final double u, final double v) {
        if (!exposed(visible, occluders, u, v)) {
            return null;
        }

        Edge horizontal = null;
        if (!frame.solidBehind(allBoxes, u - BORDER_WIDTH, v)) {
            horizontal = Edge.LEFT;
        } else if (!frame.solidBehind(allBoxes, u + BORDER_WIDTH, v)) {
            horizontal = Edge.RIGHT;
        }

        Edge vertical = null;
        if (!frame.solidBehind(allBoxes, u, v - BORDER_WIDTH)) {
            vertical = Edge.TOP;
        } else if (!frame.solidBehind(allBoxes, u, v + BORDER_WIDTH)) {
            vertical = Edge.BOTTOM;
        }

        return horizontal == null && vertical == null ? Patch.INTERIOR : new Patch(horizontal, vertical);
    }

    private static int[] tileIndices(final double[] axis, final int cells) {
        int[] tiles = new int[cells];
        for (int i = 0; i < cells; i++) {
            tiles[i] = (int) tileBase(axis[i], axis[i + 1]);
        }
        return tiles;
    }

    private static void emitMerged(final List<MeshQuad> out, final FaceFrame frame,
                                   final double[] us, final double[] vs, final Patch[] patches,
                                   final int columns, final int rows,
                                   final int[] tileU, final int[] tileV) {
        boolean[] taken = new boolean[patches.length];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int here = row * columns + col;
                if (taken[here] || patches[here] == null) {
                    continue;
                }

                int wide = 1;
                while (col + wide < columns && tileU[col + wide] == tileU[col]
                        && !taken[here + wide] && patches[here].equals(patches[here + wide])) {
                    wide++;
                }

                int tall = 1;
                while (row + tall < rows && tileV[row + tall] == tileV[row]
                        && rowMatches(patches, taken, (row + tall) * columns + col, patches[here], wide)) {
                    tall++;
                }

                for (int dy = 0; dy < tall; dy++) {
                    for (int dx = 0; dx < wide; dx++) {
                        taken[(row + dy) * columns + col + dx] = true;
                    }
                }

                emit(out, frame, us[col], vs[row], us[col + wide], vs[row + tall], patches[here]);
            }
        }
    }

    private static boolean rowMatches(final Patch[] patches, final boolean[] taken,
                                      final int start, final Patch wanted, final int wide) {
        for (int k = 0; k < wide; k++) {
            if (taken[start + k] || !wanted.equals(patches[start + k])) {
                return false;
            }
        }
        return true;
    }

    private static void emit(final List<MeshQuad> out, final FaceFrame frame,
                             final double u0, final double v0, final double u1, final double v1,
                             final Patch patch) {
        double baseU = tileBase(u0, u1);
        double baseV = tileBase(v0, v1);
        float[] uv = patch.rim()
                ? rimUv(patch, u0, v0, u1, v1, baseU, baseV)
                : interiorUv(u0, v0, u1, v1, baseU, baseV);

        float[] xyz = new float[12];
        frame.writeVertex(xyz, 0, u0, v0);
        frame.writeVertex(xyz, 3, u0, v1);
        frame.writeVertex(xyz, 6, u1, v1);
        frame.writeVertex(xyz, 9, u1, v0);

        Direction dir = frame.dir();
        out.add(new MeshQuad(xyz, uv, dir.getStepX(), dir.getStepY(), dir.getStepZ(), patch.rim()));
    }

    private static float[] interiorUv(final double u0, final double v0, final double u1, final double v1,
                                      final double baseU, final double baseV) {
        float a = (float) (tiled(u0, baseU) * INTERIOR_UV_SPAN);
        float b = (float) (tiled(v0, baseV) * INTERIOR_UV_SPAN);
        float c = (float) (tiled(u1, baseU) * INTERIOR_UV_SPAN);
        float d = (float) (tiled(v1, baseV) * INTERIOR_UV_SPAN);
        return new float[]{a, b, a, d, c, d, c, b};
    }

    private static float[] rimUv(final Patch patch, final double u0, final double v0,
                                 final double u1, final double v1,
                                 final double baseU, final double baseV) {
        float outer = (float) BORDER_UV;
        float ua;
        float ub;
        if (patch.horizontal() == Edge.LEFT) {
            ua = 0.0F;
            ub = outer;
        } else if (patch.horizontal() == Edge.RIGHT) {
            ua = 1.0F - outer;
            ub = 1.0F;
        } else {
            ua = (float) tiled(u0, baseU);
            ub = (float) tiled(u1, baseU);
        }

        float va;
        float vb;
        if (patch.vertical() == Edge.TOP) {
            va = 0.0F;
            vb = outer;
        } else if (patch.vertical() == Edge.BOTTOM) {
            va = 1.0F - outer;
            vb = 1.0F;
        } else {
            va = (float) tiled(v0, baseV);
            vb = (float) tiled(v1, baseV);
        }

        return new float[]{ua, va, ua, vb, ub, vb, ub, va};
    }

    private static double tileBase(final double lo, final double hi) {
        return Math.floor((lo + hi) * 0.5 * TEXTURE_BLOCKS);
    }

    private static double tiled(final double value, final double base) {
        return value * TEXTURE_BLOCKS - base;
    }

    private static boolean exposed(final List<Rect> visible, final List<Rect> occluders,
                                   final double u, final double v) {
        boolean onFace = false;
        for (Rect rect : visible) {
            if (rect.contains(u, v)) {
                onFace = true;
                break;
            }
        }
        if (!onFace) {
            return false;
        }
        for (Rect rect : occluders) {
            if (rect.contains(u, v)) {
                return false;
            }
        }
        return true;
    }

    private static double[] gridAxis(final List<Rect> visible, final List<Rect> cuts, final boolean uAxis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Rect rect : visible) {
            min = Math.min(min, rect.lo(uAxis));
            max = Math.max(max, rect.hi(uAxis));
        }

        List<Double> coords = new ArrayList<>();
        coords.add(min);
        coords.add(max);
        for (int tile = 1; tile < TEXTURE_BLOCKS; tile++) {
            offer(coords, tile * TILE, min, max);
        }
        for (Rect rect : visible) {
            addBandLines(coords, rect.lo(uAxis), rect.hi(uAxis), false, min, max);
        }
        for (Rect rect : cuts) {
            addBandLines(coords, rect.lo(uAxis), rect.hi(uAxis), true, min, max);
        }

        coords.sort(Double::compare);
        double[] axis = new double[coords.size()];
        int kept = 0;
        for (double c : coords) {
            if (kept == 0 || c - axis[kept - 1] > MERGE_SNAP) {
                axis[kept++] = c;
            }
        }
        double[] result = new double[kept];
        System.arraycopy(axis, 0, result, 0, kept);
        return result;
    }

    private static void addBandLines(final List<Double> coords, final double lo, final double hi,
                                     final boolean bothSides, final double min, final double max) {
        offer(coords, lo, min, max);
        offer(coords, hi, min, max);
        offer(coords, lo + BORDER_WIDTH, min, max);
        offer(coords, hi - BORDER_WIDTH, min, max);
        if (bothSides) {
            offer(coords, lo - BORDER_WIDTH, min, max);
            offer(coords, hi + BORDER_WIDTH, min, max);
        }
    }

    private static void offer(final List<Double> coords, final double value, final double min, final double max) {
        if (value > min + TOUCH && value < max - TOUCH) {
            coords.add(value);
        }
    }

    private enum Edge {
        LEFT, RIGHT, TOP, BOTTOM
    }

    private record Patch(Edge horizontal, Edge vertical) {
        static final Patch INTERIOR = new Patch(null, null);

        boolean rim() {
            return this.horizontal != null || this.vertical != null;
        }
    }

    private record Rect(double u0, double v0, double u1, double v1) {
        boolean contains(final double u, final double v) {
            return u > this.u0 + TOUCH && u < this.u1 - TOUCH
                    && v > this.v0 + TOUCH && v < this.v1 - TOUCH;
        }

        double lo(final boolean uAxis) {
            return uAxis ? this.u0 : this.v0;
        }

        double hi(final boolean uAxis) {
            return uAxis ? this.u1 : this.v1;
        }
    }

    private record FaceFrame(Direction dir, double depth) {

        static double depthOf(final AABB box, final Direction dir) {
            boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            return switch (dir.getAxis()) {
                case X -> positive ? box.maxX : box.minX;
                case Y -> positive ? box.maxY : box.minY;
                case Z -> positive ? box.maxZ : box.minZ;
            };
        }

        Rect project(final AABB box) {
            return switch (this.dir.getAxis()) {
                case Y -> new Rect(box.minX, box.minZ, box.maxX, box.maxZ);
                case Z -> new Rect(box.minX, 1.0 - box.maxY, box.maxX, 1.0 - box.minY);
                case X -> new Rect(box.minZ, 1.0 - box.maxY, box.maxZ, 1.0 - box.minY);
            };
        }

        boolean blocksOutside(final AABB box) {
            double probe = this.outward(PROBE_DEPTH);
            return switch (this.dir.getAxis()) {
                case X -> box.minX <= probe && box.maxX >= probe;
                case Y -> box.minY <= probe && box.maxY >= probe;
                case Z -> box.minZ <= probe && box.maxZ >= probe;
            };
        }

        boolean solidBehind(final List<AABB> boxes, final double u, final double v) {
            double inner = this.outward(-PROBE_DEPTH);
            double x;
            double y;
            double z;
            switch (this.dir.getAxis()) {
                case Y -> {
                    x = u;
                    y = inner;
                    z = v;
                }
                case Z -> {
                    x = u;
                    y = 1.0 - v;
                    z = inner;
                }
                default -> {
                    x = inner;
                    y = 1.0 - v;
                    z = u;
                }
            }
            for (AABB box : boxes) {
                if (x > box.minX - TOUCH && x < box.maxX + TOUCH
                        && y > box.minY - TOUCH && y < box.maxY + TOUCH
                        && z > box.minZ - TOUCH && z < box.maxZ + TOUCH) {
                    return true;
                }
            }
            return false;
        }

        void writeVertex(final float[] xyz, final int at, final double u, final double v) {
            switch (this.dir.getAxis()) {
                case Y -> {
                    xyz[at] = (float) u;
                    xyz[at + 1] = (float) this.depth;
                    xyz[at + 2] = (float) v;
                }
                case Z -> {
                    xyz[at] = (float) u;
                    xyz[at + 1] = (float) (1.0 - v);
                    xyz[at + 2] = (float) this.depth;
                }
                default -> {
                    xyz[at] = (float) this.depth;
                    xyz[at + 1] = (float) (1.0 - v);
                    xyz[at + 2] = (float) u;
                }
            }
        }

        private double outward(final double amount) {
            return this.dir.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? this.depth + amount
                    : this.depth - amount;
        }
    }
}
