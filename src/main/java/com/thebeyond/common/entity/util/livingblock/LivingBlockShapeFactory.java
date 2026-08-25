package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public class LivingBlockShapeFactory {

    private static final int SIMPLE_MIN_SIXTEENTHS = 1;
    private static final int SIMPLE_SIZE_SPREAD = 15;
    private static final int ENTROPIC_SHAPE_ATTEMPTS = 4;

    private static final float FRAME_SKIP_BRANCH_CHANCE = 0.22F;
    private static final float FRAME_SUBBRANCH_CHANCE = 0.40F;
    private static final float FRAME_CAVITY_CHANCE = 0.55F;
    private static final float FRAME_CAVITY_EXTRA_CHANCE = 0.35F;
    private static final float CLUSTER_SUBBRANCH_CHANCE = 0.40F;
    private static final double CAVITY_MIN_THICKNESS = 0.08;
    private static final double CAVITY_THICKNESS_SPREAD = 0.12;

    private static final double CROSS_CORE_MIN = 0.22;
    private static final double CROSS_CORE_SPREAD = 0.18;
    private static final float CROSS_LIMB_DROP_CHANCE = 0.30F;
    private static final float CROSS_SUBBRANCH_CHANCE = 0.45F;
    private static final double CROSS_LIMB_MIN_THICKNESS = 0.10;
    private static final double CROSS_LIMB_THICKNESS_SPREAD = 0.14;
    private static final double CROSS_LIMB_MIN_LENGTH = 0.15;
    private static final double CROSS_LIMB_LENGTH_SPREAD = 0.30;

    public static VoxelShape create(RandomSource random, boolean entropic) {
        return entropic ? createEntropic(random) : createSimple(random);
    }

    public static VoxelShape createSimple(RandomSource random) {
        float w = (SIMPLE_MIN_SIXTEENTHS + random.nextInt(SIMPLE_SIZE_SPREAD)) / 16.0F;
        float h = (SIMPLE_MIN_SIXTEENTHS + random.nextInt(SIMPLE_SIZE_SPREAD)) / 16.0F;
        float d = (SIMPLE_MIN_SIXTEENTHS + random.nextInt(SIMPLE_SIZE_SPREAD)) / 16.0F;
        return Shapes.box(0.0, 0.0, 0.0, w, h, d);
    }

    public static VoxelShape createEntropic(RandomSource random) {
        for (int attempt = 0; attempt < ENTROPIC_SHAPE_ATTEMPTS; attempt++) {
            VoxelShape shape = rollEntropicShape(random);
            if (shape.toAabbs().size() > 1) {
                return shape;
            }
        }
        return createCrossCluster(random);
    }

    private static VoxelShape rollEntropicShape(RandomSource random) {
        float roll = random.nextFloat();

        if (roll < 0.25F) {
            return createModularHollowFrame(random);
        } else if (roll < 0.50F) {
            return createModularCluster(random);
        } else if (roll < 0.75F) {
            return createTrue3DCluster(random);
        } else {
            return createCrossCluster(random);
        }
    }

    public static VoxelShape createCrossCluster(RandomSource random) {
        double coreSize = CROSS_CORE_MIN + random.nextDouble() * CROSS_CORE_SPREAD;
        double coreMin = (1.0 - coreSize) * 0.5;
        return growEntropicFrom(random, new AABB(coreMin, coreMin, coreMin,
                coreMin + coreSize, coreMin + coreSize, coreMin + coreSize));
    }

    public static VoxelShape growEntropicFrom(RandomSource random, AABB core) {
        List<AABB> components = new ArrayList<>();
        components.add(core);
        boolean grown = false;

        for (int face = 0; face < 6; face++) {
            if (random.nextFloat() < CROSS_LIMB_DROP_CHANCE) {
                continue;
            }

            AABB limb = generateCrossLimb(random, core, face);
            if (limb == null) {
                continue;
            }
            components.add(limb);
            grown = true;

            if (random.nextFloat() < CROSS_SUBBRANCH_CHANCE) {
                int subFace = random.nextInt(6);
                if (subFace != face && subFace != getOppositeFace(face)) {
                    generateTrue3DComplexBranch(random, limb, subFace, components, false);
                }
            }
        }

        if (!grown) {
            AABB limb = generateCrossLimb(random, core, random.nextInt(6));
            if (limb != null) {
                components.add(limb);
            }
        }

        return optimizeVolume(components);
    }

    private static AABB generateCrossLimb(RandomSource random, AABB core, int face) {
        double half = (CROSS_LIMB_MIN_THICKNESS + random.nextDouble() * CROSS_LIMB_THICKNESS_SPREAD) * 0.5;
        double length = CROSS_LIMB_MIN_LENGTH + random.nextDouble() * CROSS_LIMB_LENGTH_SPREAD;
        double cx = (core.minX + core.maxX) * 0.5;
        double cy = (core.minY + core.maxY) * 0.5;
        double cz = (core.minZ + core.maxZ) * 0.5;

        AABB limb = switch (face) {
            case 0 -> new AABB(cx - half, core.maxY, cz - half, cx + half, Math.min(1.0, core.maxY + length), cz + half);
            case 1 -> new AABB(cx - half, Math.max(0.0, core.minY - length), cz - half, cx + half, core.minY, cz + half);
            case 2 -> new AABB(core.maxX, cy - half, cz - half, Math.min(1.0, core.maxX + length), cy + half, cz + half);
            case 3 -> new AABB(Math.max(0.0, core.minX - length), cy - half, cz - half, core.minX, cy + half, cz + half);
            case 4 -> new AABB(cx - half, cy - half, core.maxZ, cx + half, cy + half, Math.min(1.0, core.maxZ + length));
            case 5 -> new AABB(cx - half, cy - half, Math.max(0.0, core.minZ - length), cx + half, cy + half, core.minZ);
            default -> null;
        };

        if (limb == null || limb.getXsize() < 1.0E-4 || limb.getYsize() < 1.0E-4 || limb.getZsize() < 1.0E-4) {
            return null;
        }
        return limb;
    }

    private static VoxelShape createModularHollowFrame(RandomSource random) {
        double w = 0.45 + random.nextDouble() * 0.55;
        double h = 0.35 + random.nextDouble() * 0.65;
        double d = 0.45 + random.nextDouble() * 0.55;

        double x = (1.0 - w) * 0.5;
        double y = 0.0;
        double z = (1.0 - d) * 0.5;

        List<AABB> components = new ArrayList<>();
        AABB coreBox = new AABB(x, y, z, x + w, h, z + d);
        components.add(coreBox);

        double thickness = 2.0 / 16.0;
        AABB cavity = null;
        if (w > thickness * 3.0 && d > thickness * 3.0) {
            components.clear();
            double innerMinX = x + thickness;
            double innerMaxX = x + w - thickness;
            double innerMinZ = z + thickness;
            double innerMaxZ = z + d - thickness;

            components.add(new AABB(x, y, z, x + w, h, innerMinZ));
            components.add(new AABB(x, y, innerMaxZ, x + w, h, z + d));
            components.add(new AABB(x, y, innerMinZ, innerMinX, h, innerMaxZ));
            components.add(new AABB(innerMaxX, y, innerMinZ, x + w, h, innerMaxZ));
            cavity = new AABB(innerMinX, y, innerMinZ, innerMaxX, h, innerMaxZ);
        }

        if (random.nextFloat() < FRAME_SKIP_BRANCH_CHANCE) {
            return optimizeVolume(components);
        }

        if (cavity != null && random.nextFloat() < FRAME_CAVITY_CHANCE) {
            addCavityBranch(random, cavity, components);
            if (random.nextFloat() < FRAME_CAVITY_EXTRA_CHANCE) {
                addCavityBranch(random, cavity, components);
            }
        }

        int branches = 2 + random.nextInt(3);
        boolean[] usedFaces = new boolean[4];

        for (int i = 0; i < branches; i++) {
            int face = random.nextInt(4);
            if (!usedFaces[face]) {
                usedFaces[face] = true;
                AABB lateralBranch = generateLateralFrameBranch(random, coreBox, face);
                if (lateralBranch != null) {
                    components.add(lateralBranch);
                    if (random.nextFloat() < FRAME_SUBBRANCH_CHANCE) {
                        generateTrue3DComplexBranch(random, lateralBranch, random.nextInt(6), components, false);
                    }
                }
            }
        }

        return optimizeVolume(components);
    }

    private static void addCavityBranch(RandomSource random, AABB cavity, List<AABB> components) {
        double spanX = cavity.getXsize();
        double spanY = cavity.getYsize();
        double spanZ = cavity.getZsize();
        if (spanX < 0.12 || spanY < 0.12 || spanZ < 0.12) {
            return;
        }

        double thick = CAVITY_MIN_THICKNESS + random.nextDouble() * CAVITY_THICKNESS_SPREAD;
        double tall = CAVITY_MIN_THICKNESS + random.nextDouble() * spanY * 0.6;
        double y0 = cavity.minY + random.nextDouble() * Math.max(0.0, spanY - tall);
        double y1 = Math.min(cavity.maxY, y0 + tall);

        int face = random.nextInt(4);
        boolean alongX = face < 2;
        double reach = (alongX ? spanX : spanZ) * (0.35 + random.nextDouble() * 0.55);
        double lateralSpan = alongX ? spanZ : spanX;
        double lateralMin = (alongX ? cavity.minZ : cavity.minX) + random.nextDouble() * Math.max(0.0, lateralSpan - thick);
        double lateralMax = lateralMin + Math.min(thick, lateralSpan);

        AABB branch;
        if (alongX) {
            double x0 = face == 0 ? cavity.minX : cavity.maxX - reach;
            double x1 = face == 0 ? cavity.minX + reach : cavity.maxX;
            branch = new AABB(x0, y0, lateralMin, x1, y1, lateralMax);
        } else {
            double z0 = face == 2 ? cavity.minZ : cavity.maxZ - reach;
            double z1 = face == 2 ? cavity.minZ + reach : cavity.maxZ;
            branch = new AABB(lateralMin, y0, z0, lateralMax, y1, z1);
        }

        if (branch.getXsize() > 1.0E-4 && branch.getYsize() > 1.0E-4 && branch.getZsize() > 1.0E-4) {
            components.add(branch);
        }
    }

    private static VoxelShape createModularCluster(RandomSource random) {
        double baseW = 0.25 + random.nextDouble() * 0.75;
        double baseH = 0.12 + random.nextDouble() * 0.88; 
        double baseD = 0.25 + random.nextDouble() * 0.75;

        double baseX = (1.0 - baseW) * 0.5;
        double baseY = 0.0;
        double baseZ = (1.0 - baseD) * 0.5;

        List<AABB> components = new ArrayList<>();
        AABB coreBox = new AABB(baseX, baseY, baseZ, baseX + baseW, baseH, baseZ + baseD);
        components.add(coreBox);

        int extraPieces = random.nextInt(3);
        for (int i = 0; i < extraPieces; i++) {
            double ew = 0.2 + random.nextDouble() * 0.4;
            double eh = 0.2 + random.nextDouble() * 0.4;
            double ed = 0.2 + random.nextDouble() * 0.4;
            double ex = Mth.clamp(baseX + (random.nextDouble() - 0.5) * 0.4, 0.0, 1.0 - ew);
            double ey = Mth.clamp(baseY + random.nextDouble() * (baseH * 0.5), 0.0, 1.0 - eh);
            double ez = Mth.clamp(baseZ + (random.nextDouble() - 0.5) * 0.4, 0.0, 1.0 - ed);
            components.add(new AABB(ex, ey, ez, ex + ew, ey + eh, ez + ed));
        }

        int branches = 1 + random.nextInt(4);
        boolean[] usedFaces = new boolean[5];

        for (int i = 0; i < branches; i++) {
            int face = random.nextInt(5);
            if (!usedFaces[face]) {
                usedFaces[face] = true;
                AABB branch = generateModularBranch(random, coreBox, face);
                if (branch != null) {
                    components.add(branch);
                    if (random.nextFloat() < CLUSTER_SUBBRANCH_CHANCE) {
                        generateTrue3DComplexBranch(random, branch, random.nextInt(6), components, false);
                    }
                }
            }
        }

        return optimizeVolume(components);
    }

    private static VoxelShape createTrue3DCluster(RandomSource random) {
        double coreMinX = 0.28 + random.nextDouble() * 0.15;
        double coreMinY = 0.28 + random.nextDouble() * 0.15;
        double coreMinZ = 0.28 + random.nextDouble() * 0.15;
        double coreMaxX = coreMinX + 0.3 + random.nextDouble() * 0.15;
        double coreMaxY = coreMinY + 0.3 + random.nextDouble() * 0.15;
        double coreMaxZ = coreMinZ + 0.3 + random.nextDouble() * 0.15;

        AABB coreBox = new AABB(
            Mth.clamp(coreMinX, 0.2, 0.5),
            Mth.clamp(coreMinY, 0.2, 0.5),
            Mth.clamp(coreMinZ, 0.2, 0.5),
            Mth.clamp(coreMaxX, 0.5, 0.8),
            Mth.clamp(coreMaxY, 0.5, 0.8),
            Mth.clamp(coreMaxZ, 0.5, 0.8)
        );

        List<AABB> components = new ArrayList<>();
        components.add(coreBox);

        boolean[] usedFaces = new boolean[6];
        int initialBranches = 3 + random.nextInt(4); 

        for (int i = 0; i < initialBranches; i++) {
            int face = random.nextInt(6);
            if (!usedFaces[face]) {
                usedFaces[face] = true;
                generateTrue3DComplexBranch(random, coreBox, face, components, true);
            }
        }

        for (int face = 0; face < 6; face++) {
            if (!usedFaces[face] && random.nextFloat() < 0.70f) {
                usedFaces[face] = true;
                generateTrue3DComplexBranch(random, coreBox, face, components, true);
            }
        }

        return optimizeVolume(components);
    }

    private static void generateTrue3DComplexBranch(RandomSource random, AABB refBox, int face, List<AABB> components, boolean allowSubBranches) {
        double thicknessW = 0.12 + random.nextDouble() * 0.22;
        double thicknessH = 0.12 + random.nextDouble() * 0.22;
        double thicknessD = 0.12 + random.nextDouble() * 0.22;
        double length = 0.2 + random.nextDouble() * 0.5;

        double minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;

        switch (face) {
            case 0: // Y+ 
                minY = refBox.maxY; maxY = Math.min(1.0, minY + length);
                minX = Mth.clamp(refBox.minX + (random.nextDouble() - 0.5) * refBox.getXsize(), 0.0, 1.0 - thicknessW); maxX = Math.min(1.0, minX + thicknessW);
                minZ = Mth.clamp(refBox.minZ + (random.nextDouble() - 0.5) * refBox.getZsize(), 0.0, 1.0 - thicknessD); maxZ = Math.min(1.0, minZ + thicknessD);
                break;
            case 1: // Y- 
                maxY = refBox.minY; minY = Math.max(0.0, maxY - length);
                minX = Mth.clamp(refBox.minX + (random.nextDouble() - 0.5) * refBox.getXsize(), 0.0, 1.0 - thicknessW); maxX = Math.min(1.0, minX + thicknessW);
                minZ = Mth.clamp(refBox.minZ + (random.nextDouble() - 0.5) * refBox.getZsize(), 0.0, 1.0 - thicknessD); maxZ = Math.min(1.0, minZ + thicknessD);
                break;
            case 2: // X+ 
                minX = refBox.maxX; maxX = Math.min(1.0, minX + length);
                minY = Mth.clamp(refBox.minY + (random.nextDouble() - 0.5) * refBox.getYsize(), 0.0, 1.0 - thicknessH); maxY = Math.min(1.0, minY + thicknessH);
                minZ = Mth.clamp(refBox.minZ + (random.nextDouble() - 0.5) * refBox.getZsize(), 0.0, 1.0 - thicknessD); maxZ = Math.min(1.0, minZ + thicknessD);
                break;
            case 3: // X- 
                maxX = refBox.minX; minX = Math.max(0.0, maxX - length);
                minY = Mth.clamp(refBox.minY + (random.nextDouble() - 0.5) * refBox.getYsize(), 0.0, 1.0 - thicknessH); maxY = Math.min(1.0, minY + thicknessH);
                minZ = Mth.clamp(refBox.minZ + (random.nextDouble() - 0.5) * refBox.getZsize(), 0.0, 1.0 - thicknessD); maxZ = Math.min(1.0, minZ + thicknessD);
                break;
            case 4: // Z+ 
                minZ = refBox.maxZ; maxZ = Math.min(1.0, minZ + length);
                minX = Mth.clamp(refBox.minX + (random.nextDouble() - 0.5) * refBox.getXsize(), 0.0, 1.0 - thicknessW); maxX = Math.min(1.0, minX + thicknessW);
                minY = Mth.clamp(refBox.minY + (random.nextDouble() - 0.5) * refBox.getYsize(), 0.0, 1.0 - thicknessH); maxY = Math.min(1.0, minY + thicknessH);
                break;
            case 5: // Z- 
                maxZ = refBox.minZ; minZ = Math.max(0.0, maxZ - length);
                minX = Mth.clamp(refBox.minX + (random.nextDouble() - 0.5) * refBox.getXsize(), 0.0, 1.0 - thicknessW); maxX = Math.min(1.0, minX + thicknessW);
                minY = Mth.clamp(refBox.minY + (random.nextDouble() - 0.5) * refBox.getYsize(), 0.0, 1.0 - thicknessH); maxY = Math.min(1.0, minY + thicknessH);
                break;
        }

        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return;
        
        AABB branchBounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        double t = 0.06;
        boolean makeHollow = (maxX - minX > t*3) && (maxY - minY > t*3) && (maxZ - minZ > t*3) && random.nextFloat() < 0.35f;

        if (makeHollow) {
            if (face == 0 || face == 1) {
                components.add(new AABB(minX, minY, minZ, maxX, maxY, minZ + t));
                components.add(new AABB(minX, minY, maxZ - t, maxX, maxY, maxZ));
                components.add(new AABB(minX, minY, minZ + t, minX + t, maxY, maxZ - t));
                components.add(new AABB(maxX - t, minY, minZ + t, maxX, maxY, maxZ - t));
            } else if (face == 2 || face == 3) {
                components.add(new AABB(minX, minY, minZ, maxX, minY + t, maxZ));
                components.add(new AABB(minX, maxY - t, minZ, maxX, maxY, maxZ));
                components.add(new AABB(minX, minY + t, minZ, maxX, maxY - t, minZ + t));
                components.add(new AABB(minX, minY + t, maxZ - t, maxX, maxY - t, maxZ));
            } else {
                components.add(new AABB(minX, minY, minZ, maxX, minY + t, maxZ));
                components.add(new AABB(minX, maxY - t, minZ, maxX, maxY, maxZ));
                components.add(new AABB(minX, minY + t, minZ, minX + t, maxY - t, maxZ));
                components.add(new AABB(maxX - t, minY + t, minZ, maxX, maxY - t, maxZ));
            }
        } else {
            components.add(branchBounds);
        }

        if (allowSubBranches && random.nextFloat() < 0.45f) {
            int subFace = random.nextInt(6);
            if (subFace != face && subFace != getOppositeFace(face)) {
                generateTrue3DComplexBranch(random, branchBounds, subFace, components, false);
            }
        }
    }

    private static int getOppositeFace(int face) {
        switch (face) {
            case 0: return 1; case 1: return 0;
            case 2: return 3; case 3: return 2;
            case 4: return 5; case 5: return 4;
            default: return 0;
        }
    }

    private static AABB generateLateralFrameBranch(RandomSource random, AABB coreBox, int face) {
        double extW = 0.15 + random.nextDouble() * 0.35;
        double extH = 0.15 + random.nextDouble() * (coreBox.getYsize() * 0.7);
        double extD = 0.15 + random.nextDouble() * 0.35;

        double minX = 0, minY = coreBox.minY, minZ = 0, maxX = 0, maxY = Math.min(1.0, coreBox.minY + extH), maxZ = 0;

        switch (face) {
            case 0:
                minX = coreBox.maxX; maxX = Math.min(1.0, minX + extW);
                minZ = Mth.clamp(coreBox.minZ + (coreBox.getZsize() - extD) * 0.5, 0.0, 1.0 - extD);
                maxZ = Math.min(1.0, minZ + extD);
                break;
            case 1:
                maxX = coreBox.minX; minX = Math.max(0.0, maxX - extW);
                minZ = Mth.clamp(coreBox.minZ + (coreBox.getZsize() - extD) * 0.5, 0.0, 1.0 - extD);
                maxZ = Math.min(1.0, minZ + extD);
                break;
            case 2:
                minZ = coreBox.maxZ; maxZ = Math.min(1.0, minZ + extD);
                minX = Mth.clamp(coreBox.minX + (coreBox.getXsize() - extW) * 0.5, 0.0, 1.0 - extW);
                maxX = Math.min(1.0, minX + extW);
                break;
            case 3:
                maxZ = coreBox.minZ; minZ = Math.max(0.0, maxZ - extD);
                minX = Mth.clamp(coreBox.minX + (coreBox.getXsize() - extW) * 0.5, 0.0, 1.0 - extW);
                maxX = Math.min(1.0, minX + extW);
                break;
            default: return null;
        }

        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return null;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static AABB generateModularBranch(RandomSource random, AABB coreBox, int face) {
        double extW = 0.18 + random.nextDouble() * 0.38;
        double extH = 0.18 + random.nextDouble() * 0.38;
        double extD = 0.18 + random.nextDouble() * 0.38;

        double minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;

        switch (face) {
            case 0:
                minY = coreBox.maxY; maxY = Math.min(1.0, minY + extH);
                minX = Mth.clamp(coreBox.minX + (coreBox.getXsize() - extW) * 0.5, 0.0, 1.0 - extW); maxX = Math.min(1.0, minX + extW);
                minZ = Mth.clamp(coreBox.minZ + (coreBox.getZsize() - extD) * 0.5, 0.0, 1.0 - extD); maxZ = Math.min(1.0, minZ + extD);
                break;
            case 1:
                minX = coreBox.maxX; maxX = Math.min(1.0, minX + extW);
                minY = Mth.clamp(coreBox.minY, 0.0, 1.0 - extH); maxY = Math.min(1.0, minY + extH);
                minZ = Mth.clamp(coreBox.minZ + (coreBox.getZsize() - extD) * 0.5, 0.0, 1.0 - extD); maxZ = Math.min(1.0, minZ + extD);
                break;
            case 2:
                maxX = coreBox.minX; minX = Math.max(0.0, maxX - extW);
                minY = Mth.clamp(coreBox.minY, 0.0, 1.0 - extH); maxY = Math.min(1.0, minY + extH);
                minZ = Mth.clamp(coreBox.minZ + (coreBox.getZsize() - extD) * 0.5, 0.0, 1.0 - extD); maxZ = Math.min(1.0, minZ + extD);
                break;
            case 3:
                minZ = coreBox.maxZ; maxZ = Math.min(1.0, minZ + extD);
                minX = Mth.clamp(coreBox.minX + (coreBox.getXsize() - extW) * 0.5, 0.0, 1.0 - extW); maxX = Math.min(1.0, minX + extW);
                minY = Mth.clamp(coreBox.minY, 0.0, 1.0 - extH); maxY = Math.min(1.0, minY + extH);
                break;
            case 4:
                maxZ = coreBox.minZ; minZ = Math.max(0.0, maxZ - extD);
                minX = Mth.clamp(coreBox.minX + (coreBox.getXsize() - extW) * 0.5, 0.0, 1.0 - extW); maxX = Math.min(1.0, minX + extW);
                minY = Mth.clamp(coreBox.minY, 0.0, 1.0 - extH); maxY = Math.min(1.0, minY + extH);
                break;
            default: return null;
        }

        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return null;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static VoxelShape optimizeVolume(List<AABB> components) {
        VoxelShape finalShape = Shapes.empty();
        for (AABB box : components) {
            finalShape = Shapes.joinUnoptimized(finalShape, Shapes.create(box), BooleanOp.OR);
        }
        return finalShape.isEmpty() ? Shapes.block() : finalShape.optimize();
    }
}