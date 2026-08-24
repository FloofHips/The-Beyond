package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public final class LivingBlockFeatures {

    public enum Kind {
        HOLE,
        SPIKE
    }

    public enum SizeClass {
        SMALL,
        MEDIUM,
        LARGE
    }

    public static final int MAX_GROWTH_STAGE = 32;

    public static final int MAX_FEATURES = 4;
    public static final int MIN_FEATURES = 2;

    public static final double SPIKE_MAX_PROTRUSION = 0.0;

    public static final double MAX_FOOTPRINT = 1.0;

    private static final int FIRST_SPAWN_STAGE = 2;
    private static final int SPAWN_SPAN = 16;
    private static final int GROW_SPAN_MIN = 8;
    private static final int GROW_SPAN_SPREAD = 9;
    private static final int PLACEMENT_ATTEMPTS = 4;
    private static final int SPIKE_ONE_IN = 4;

    private static final double GRID = 1.0 / 16.0;

    private static final double RIM = 2.0 / 16.0;

    private static final double FLOOR = 1.0 / 16.0;

    private static final double PROBE = 1.0E-4;
    private static final double TOUCH = 1.0E-9;

    private static final double MIN_PIECE = 1.0E-6;

    private static final int CUT_PIECE_HEADROOM = 5;

    private static final double LARGE_CORE = 8.0 / 16.0;
    private static final double MEDIUM_CORE = 5.0 / 16.0;

    private static final int LARGE_TIERS = 3;
    private static final int MEDIUM_TIERS = 2;
    private static final int SMALL_TIERS = 1;

    private static final double[] HOLE_EXTENT = {3.0 / 16.0, 5.0 / 16.0, 8.0 / 16.0};
    private static final double[] HOLE_DEPTH = {2.0 / 16.0, 3.0 / 16.0, 4.0 / 16.0};
    private static final double[] SPIKE_CROSS = {2.0 / 16.0, 3.0 / 16.0, 4.0 / 16.0};
    private static final double[] SPIKE_LENGTH = {2.0 / 16.0, 3.0 / 16.0, 4.0 / 16.0};

    private static final Direction[] FACES = Direction.values();
    private static final Direction.Axis[] AXES = Direction.Axis.values();

    private LivingBlockFeatures() {
    }

    public record Feature(Kind kind, int host, Direction face, double plane,
                          double aMin, double aMax, double bMin, double bMax,
                          double target, int spawnStage, int growSpan) {

        public double sizeAt(final int stage) {
            if (stage < this.spawnStage) {
                return 0.0;
            }
            int steps = (int) Math.round(this.target / GRID);
            if (steps <= 1) {
                return this.target;
            }
            double progress = Math.min(1.0, (double) (stage - this.spawnStage) / this.growSpan);
            return (1 + (int) Math.floor((steps - 1) * progress)) * GRID;
        }

        public AABB volume(final double size) {
            boolean positive = this.face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            boolean outward = this.kind == Kind.SPIKE;
            double lo = this.plane;
            double hi = this.plane;
            if (positive == outward) {
                hi += size;
            } else {
                lo -= size;
            }
            return switch (this.face.getAxis()) {
                case X -> new AABB(lo, this.aMin, this.bMin, hi, this.aMax, this.bMax);
                case Y -> new AABB(this.aMin, lo, this.bMin, this.aMax, hi, this.bMax);
                case Z -> new AABB(this.aMin, this.bMin, lo, this.aMax, this.bMax, hi);
            };
        }
    }

    public record Plan(List<Feature> features, int rejections, @Nullable String firstRejection) {

        public static final Plan EMPTY = new Plan(List.of(), 0, null);
    }

    public static SizeClass sizeClass(final List<AABB> baseBoxes) {
        double coreMin = 0.0;
        for (AABB box : baseBoxes) {
            coreMin = Math.max(coreMin, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())));
        }
        if (coreMin >= LARGE_CORE) {
            return SizeClass.LARGE;
        }
        return coreMin >= MEDIUM_CORE ? SizeClass.MEDIUM : SizeClass.SMALL;
    }

    private static int tierCount(final SizeClass sizeClass) {
        return switch (sizeClass) {
            case LARGE -> LARGE_TIERS;
            case MEDIUM -> MEDIUM_TIERS;
            case SMALL -> SMALL_TIERS;
        };
    }

    public static Plan generate(final List<AABB> baseBoxes, final int featureSeed) {
        if (featureSeed == 0 || baseBoxes.isEmpty()) {
            return Plan.EMPTY;
        }

        int boxCount = baseBoxes.size();
        AABB baseBounds = bounds(baseBoxes);
        int tiers = tierCount(sizeClass(baseBoxes));

        RandomSource random = RandomSource.create(featureSeed);
        int count = MIN_FEATURES + random.nextInt(MAX_FEATURES - MIN_FEATURES + 1);

        List<Feature> accepted = new ArrayList<>(count);
        int rejections = 0;
        String firstRejection = null;

        for (int index = 0; index < count; index++) {
            int spawnStage = FIRST_SPAWN_STAGE
                    + (count <= 1 ? 0 : Math.round((float) index * SPAWN_SPAN / (count - 1)));

            for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
                int startHost = random.nextInt(boxCount);
                int startFace = random.nextInt(FACES.length);
                Kind kind = random.nextInt(SPIKE_ONE_IN) == 0 ? Kind.SPIKE : Kind.HOLE;
                int tier = random.nextInt(tiers);
                double alongA = random.nextDouble();
                double alongB = random.nextDouble();
                int growSpan = Math.max(1, Math.min(GROW_SPAN_MIN + random.nextInt(GROW_SPAN_SPREAD),
                        MAX_GROWTH_STAGE - spawnStage));

                Attempt result = scan(baseBoxes, baseBounds, accepted, startHost, startFace, kind, tier,
                        alongA, alongB, spawnStage, growSpan);
                if (result.feature() == null) {
                    Kind other = kind == Kind.HOLE ? Kind.SPIKE : Kind.HOLE;
                    Attempt fallback = scan(baseBoxes, baseBounds, accepted, startHost, startFace, other, tier,
                            alongA, alongB, spawnStage, growSpan);
                    if (fallback.feature() != null) {
                        result = fallback;
                    }
                }

                if (result.feature() != null) {
                    accepted.add(result.feature());
                    break;
                }

                rejections++;
                if (firstRejection == null) {
                    firstRejection = "kind=" + kind + " tier=" + tier + " box=" + startHost
                            + " dir=" + FACES[startFace] + " rule=" + result.rule();
                }
            }
        }

        return new Plan(List.copyOf(accepted), rejections, firstRejection);
    }

    private record Attempt(@Nullable Feature feature, String rule) {
    }

    private static final Attempt R1 = new Attempt(null, "R1");
    private static final Attempt R2 = new Attempt(null, "R2");
    private static final Attempt R3 = new Attempt(null, "R3");
    private static final Attempt R4 = new Attempt(null, "R4");
    private static final Attempt R5 = new Attempt(null, "R5");
    private static final Attempt R6 = new Attempt(null, "R6");
    private static final Attempt R7 = new Attempt(null, "R7");
    private static final Attempt R8 = new Attempt(null, "R8");

    private static Attempt scan(final List<AABB> baseBoxes, final AABB baseBounds, final List<Feature> accepted,
                                final int startHost, final int startFace, final Kind kind, final int tier,
                                final double alongA, final double alongB,
                                final int spawnStage, final int growSpan) {
        Attempt last = R2;
        int boxCount = baseBoxes.size();
        for (int i = 0; i < boxCount; i++) {
            int host = (startHost + i) % boxCount;
            for (int j = 0; j < FACES.length; j++) {
                Direction face = FACES[(startFace + j) % FACES.length];
                for (int step = tier; step >= 0; step--) {
                    Attempt result = place(baseBoxes, baseBounds, accepted, host, face, kind, step,
                            alongA, alongB, spawnStage, growSpan);
                    if (result.feature() != null) {
                        return result;
                    }
                    last = result;
                }
            }
        }
        return last;
    }

    private static Attempt place(final List<AABB> baseBoxes, final AABB baseBounds, final List<Feature> accepted,
                                 final int host, final Direction face, final Kind kind, final int tier,
                                 final double alongA, final double alongB,
                                 final int spawnStage, final int growSpan) {
        AABB box = baseBoxes.get(host);
        Direction.Axis normal = face.getAxis();
        Direction.Axis axisA = firstFree(normal);
        Direction.Axis axisB = secondFree(normal);

        double extent = kind == Kind.HOLE ? HOLE_EXTENT[tier] : SPIKE_CROSS[tier];
        double target = kind == Kind.HOLE ? HOLE_DEPTH[tier] : SPIKE_LENGTH[tier];

        double roomA = size(box, axisA) - extent - 2.0 * RIM;
        double roomB = size(box, axisB) - extent - 2.0 * RIM;
        if (roomA < -TOUCH || roomB < -TOUCH) {
            return R2;
        }

        double aMin = min(box, axisA) + RIM + snapDown(Math.max(0.0, roomA) * alongA);
        double bMin = min(box, axisB) + RIM + snapDown(Math.max(0.0, roomB) * alongB);
        double aMax = aMin + extent;
        double bMax = bMin + extent;

        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = positive ? max(box, normal) : min(box, normal);

        if (covered(baseBoxes, host, face, plane, axisA, aMin, aMax, axisB, bMin, bMax)) {
            return R1;
        }

        Feature candidate = new Feature(kind, host, face, plane, aMin, aMax, bMin, bMax,
                target, spawnStage, growSpan);

        for (Feature other : accepted) {
            if (other.host() == host && other.face() == face
                    && overlaps(aMin - RIM, aMax + RIM, other.aMin(), other.aMax())
                    && overlaps(bMin - RIM, bMax + RIM, other.bMin(), other.bMax())) {
                return R4;
            }
        }

        AABB volume = candidate.volume(target);

        if (kind == Kind.HOLE) {
            double used = target;
            for (Feature other : accepted) {
                if (other.kind() == Kind.HOLE && other.host() == host && other.face().getAxis() == normal) {
                    used += other.target();
                }
            }
            if (used > size(box, normal) - FLOOR + TOUCH) {
                return R3;
            }

            AABB cut = volume.inflate(RIM);
            for (Feature other : accepted) {
                if (other.kind() == Kind.HOLE && intersects(cut, other.volume(other.target()))) {
                    return R6;
                }
            }
            if (!planesClear(box, accepted, volume, host)) {
                return R7;
            }
            return new Attempt(candidate, "");
        }

        int boxCount = baseBoxes.size();
        for (int i = 0; i < boxCount; i++) {
            if (i != host && intersects(volume, baseBoxes.get(i))) {
                return R5;
            }
        }
        for (Feature other : accepted) {
            if (other.kind() == Kind.SPIKE && intersects(volume, other.volume(other.target()))) {
                return R5;
            }
        }

        double reach = positive
                ? volume.max(normal) - baseBounds.max(normal)
                : baseBounds.min(normal) - volume.min(normal);
        double allowed = face == Direction.DOWN ? 0.0 : SPIKE_MAX_PROTRUSION;
        if (reach > allowed + TOUCH) {
            return R8;
        }
        if (reach > 0.0 && size(baseBounds, normal) + reach > MAX_FOOTPRINT + TOUCH) {
            return R8;
        }

        return new Attempt(candidate, "");
    }

    private static boolean planesClear(final AABB box, final List<Feature> accepted,
                                       final AABB candidateCut, final int host) {
        List<AABB> cuts = new ArrayList<>(accepted.size() + 1);
        for (Feature other : accepted) {
            if (other.kind() == Kind.HOLE && other.host() == host) {
                cuts.add(other.volume(other.target()));
            }
        }
        cuts.add(candidateCut);

        double[] planes = new double[2 + 2 * cuts.size()];
        for (Direction.Axis axis : AXES) {
            planes[0] = min(box, axis);
            planes[1] = max(box, axis);
            int at = 2;
            for (AABB cut : cuts) {
                planes[at++] = min(cut, axis);
                planes[at++] = max(cut, axis);
            }

            for (int i = 0; i < planes.length; i++) {
                for (int j = i + 1; j < planes.length; j++) {
                    double gap = Math.abs(planes[i] - planes[j]);
                    if (gap > TOUCH && gap < FLOOR - TOUCH) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static List<AABB> apply(final List<AABB> baseBoxes, final List<Feature> features, final int stage) {
        if (features.isEmpty()) {
            return baseBoxes;
        }

        List<AABB> solid = new ArrayList<>(baseBoxes.size() + features.size());
        solid.addAll(baseBoxes);
        List<AABB> cuts = new ArrayList<>(features.size());

        for (Feature feature : features) {
            double size = feature.sizeAt(stage);
            if (size <= 0.0) {
                continue;
            }
            if (feature.kind() == Kind.SPIKE) {
                solid.add(feature.volume(size));
            } else {
                cuts.add(feature.volume(size));
            }
        }

        if (cuts.isEmpty()) {
            return List.copyOf(solid);
        }

        List<AABB> current = solid;
        for (AABB cut : cuts) {
            List<AABB> next = new ArrayList<>(current.size() + CUT_PIECE_HEADROOM);
            for (AABB piece : current) {
                subtract(piece, cut, next);
            }
            current = next;
        }
        return List.copyOf(current);
    }

    public static void subtract(final AABB piece, final AABB cut, final List<AABB> out) {
        double x0 = Math.max(piece.minX, cut.minX);
        double x1 = Math.min(piece.maxX, cut.maxX);
        double y0 = Math.max(piece.minY, cut.minY);
        double y1 = Math.min(piece.maxY, cut.maxY);
        double z0 = Math.max(piece.minZ, cut.minZ);
        double z1 = Math.min(piece.maxZ, cut.maxZ);

        if (x1 - x0 <= TOUCH || y1 - y0 <= TOUCH || z1 - z0 <= TOUCH) {
            out.add(piece);
            return;
        }

        offer(out, piece.minX, piece.minY, piece.minZ, x0, piece.maxY, piece.maxZ);
        offer(out, x1, piece.minY, piece.minZ, piece.maxX, piece.maxY, piece.maxZ);
        offer(out, x0, piece.minY, piece.minZ, x1, y0, piece.maxZ);
        offer(out, x0, y1, piece.minZ, x1, piece.maxY, piece.maxZ);
        offer(out, x0, y0, piece.minZ, x1, y1, z0);
        offer(out, x0, y0, z1, x1, y1, piece.maxZ);
    }

    private static void offer(final List<AABB> out, final double minX, final double minY, final double minZ,
                              final double maxX, final double maxY, final double maxZ) {
        if (maxX - minX < MIN_PIECE || maxY - minY < MIN_PIECE || maxZ - minZ < MIN_PIECE) {
            return;
        }
        out.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public static AABB bounds(final List<AABB> boxes) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (AABB box : boxes) {
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX);
            maxY = Math.max(maxY, box.maxY);
            maxZ = Math.max(maxZ, box.maxZ);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static AABB union(final AABB first, final AABB second) {
        return new AABB(
                Math.min(first.minX, second.minX),
                Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY),
                Math.max(first.maxZ, second.maxZ));
    }

    private static boolean covered(final List<AABB> boxes, final int host, final Direction face, final double plane,
                                   final Direction.Axis axisA, final double aMin, final double aMax,
                                   final Direction.Axis axisB, final double bMin, final double bMax) {
        Direction.Axis normal = face.getAxis();
        double probe = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? plane + PROBE : plane - PROBE;
        int boxCount = boxes.size();
        for (int i = 0; i < boxCount; i++) {
            if (i == host) {
                continue;
            }
            AABB other = boxes.get(i);
            if (min(other, normal) > probe || max(other, normal) < probe) {
                continue;
            }
            if (overlaps(aMin, aMax, min(other, axisA), max(other, axisA))
                    && overlaps(bMin, bMax, min(other, axisB), max(other, axisB))) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersects(final AABB first, final AABB second) {
        return Math.min(first.maxX, second.maxX) - Math.max(first.minX, second.minX) > TOUCH
                && Math.min(first.maxY, second.maxY) - Math.max(first.minY, second.minY) > TOUCH
                && Math.min(first.maxZ, second.maxZ) - Math.max(first.minZ, second.minZ) > TOUCH;
    }

    private static boolean overlaps(final double lo, final double hi, final double otherLo, final double otherHi) {
        return Math.min(hi, otherHi) - Math.max(lo, otherLo) > TOUCH;
    }

    private static double snapDown(final double value) {
        return Math.floor(value / GRID) * GRID;
    }

    private static Direction.Axis firstFree(final Direction.Axis normal) {
        return normal == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    private static Direction.Axis secondFree(final Direction.Axis normal) {
        return normal == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    private static double min(final AABB box, final Direction.Axis axis) {
        return box.min(axis);
    }

    private static double max(final AABB box, final Direction.Axis axis) {
        return box.max(axis);
    }

    private static double size(final AABB box, final Direction.Axis axis) {
        return box.max(axis) - box.min(axis);
    }
}
