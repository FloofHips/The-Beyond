package com.thebeyond.common.entity.util.livingblock;

import com.thebeyond.TheBeyond;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class TrinketGrowth {
    static ResourceLocation HOLE_BIG = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/hole_big.png");
    static ResourceLocation HOLE_MEDIUM = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/hole_medium.png");
    static ResourceLocation HOLE_SMALL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/hole_small.png");
    static ResourceLocation SPIKE_BASE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/spike_base.png");

    public enum Kind {
        HOLE,
        SPIKE
    }

    public enum SizeClass {
        NONE,
        SMALL,
        MEDIUM,
        LARGE;

        public int toInt() {
            if (this.equals(NONE)) return 0;
            if (this.equals(SMALL)) return 1;
            if (this.equals(MEDIUM)) return 2;
            return 3;
        }
    }

    private static final int[] HOLE_EXTENT = {0, 3, 4, 7};

    public record Feature(Kind kind, Direction face, int x, int y, int spawnStage, int growSpan, SizeClass maxSize) {

        public SizeClass sizeAt(final int stage) {
            if (stage < this.spawnStage) {
                return SizeClass.NONE;
            }

            float progress = (float) Math.min(1.0f, (double) (stage - this.spawnStage) / this.growSpan);

            return trimSize(floatToSize(progress), maxSize);
        }

        public int getWidth(SizeClass size) {
           if (kind == Kind.SPIKE) return size == SizeClass.NONE ? 0 : 4;
           return size == SizeClass.LARGE ? 7 : size == SizeClass.MEDIUM ? 4 : 3;
        }

        public ResourceLocation getTexture(SizeClass size) {
            if (kind == Kind.SPIKE) return SPIKE_BASE;
            return size == SizeClass.LARGE ? HOLE_BIG : size == SizeClass.MEDIUM ? HOLE_MEDIUM : HOLE_SMALL;
        }
    }

    public static List<Feature> generate(List<AABB> baseBoxes, final RandomSource random) {
        List<Feature> full = new ArrayList<>(List.of());

        for (Direction d : Direction.values()){
            byte[][] empty = fillByteArray(random);
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    byte size = empty[i][j];
                    if (size == 1 || size == 2 || size == 3) {
                        int spawnStage = random.nextInt(10) + i + j;
                        Kind kind = size == 2 ? (random.nextFloat() < 0.3 ? Kind.SPIKE : Kind.HOLE) : Kind.HOLE;

                        full.add(new Feature(kind, d, i, j, spawnStage, spawnStage + random.nextInt(20), kind == Kind.SPIKE ? intToSize(random.nextInt(1, 4)) : intToSize(size)));
                    }
                }
            }
        }

        return full;
    }

    public static byte[][] fillByteArray(final RandomSource random) {
        byte[][] empty = new byte[][] {
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }
        };


        if (random.nextBoolean()) {
            int x = random.nextInt(10);
            int y = random.nextInt(10);
            fillDeadSpace(empty, x, y, 3);
        }

        for (int i = 0; i < 4; i++) {
            int x = random.nextInt(10);
            int y = random.nextInt(10);
            if (checkDeadSpace(empty, x, y, 3)) fillDeadSpace(empty, x, y, 3);
        }

        for (int i = 0; i < 5; i++) {
            if (random.nextBoolean()) continue;
            int x = random.nextInt(12);
            int y = random.nextInt(12);
            if (checkDeadSpace(empty, x, y, 2)) fillDeadSpace(empty, x, y, 2);
        }
        for (int i = 0; i < 4; i++) {
            if (random.nextBoolean()) continue;
            int x = random.nextInt(13);
            int y = random.nextInt(13);
            if (checkDeadSpace(empty, x, y, 1)) fillDeadSpace(empty, x, y, 1);
        }

        return empty;
    }

    private static boolean checkDeadSpace(byte[][] empty, int x, int y, int size) {
        if (size == 1) {
            if (!checkSmallSizeCell(empty, x-1, y)) return false;
            if (!checkSmallSizeCell(empty, x+1, y)) return false;
            if (!checkSmallSizeCell(empty, x, y-1)) return false;
            return checkSmallSizeCell(empty, x, y + 1);
        }

        int side = 11;
        int cutout = 5;

        if (size==2) {
            side = 5;
            cutout = 2;
        }

        int xStart = x - cutout;
        int yStart = y - cutout;

        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                if (i < cutout && j < cutout) continue;
                if (xStart+i < 0 || yStart+j < 0 || xStart+i >= empty.length || yStart+j >= empty.length) continue;
                if (isOccupied(size, empty[xStart+i][yStart+j])) return false;
            }
        }

        return true;
    }

    private static boolean isOccupied(int size, int value) {
        if (value>0) return true;
        return value == -size;
    }

    private static void fillDeadSpace(byte[][] empty, int x, int y, int size) {
        if (size == 1) {
            fillSmallSizeCell(empty, x-1, y);
            fillSmallSizeCell(empty, x+1, y);
            fillSmallSizeCell(empty, x, y-1);
            fillSmallSizeCell(empty, x, y+1);
            empty[x][y] = (byte) 1;
            return;
        }

        int side = 11;
        int cutout = 5;

        if (size==2) {
            side = 5;
            cutout = 2;
        }

        int xStart = x - cutout;
        int yStart = y - cutout;

        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                if (i < cutout && j < cutout) continue;
                if (xStart+i < 0 || yStart+j < 0 || xStart+i >= empty.length || yStart+j >= empty.length ) continue;
                empty[xStart+i][yStart+j] = (byte) -size;
            }
        }

        empty[x][y] = (byte) size;
        return;
    }

    private static void fillSmallSizeCell(byte[][] empty, int x, int y) {
        if (x < 0 || x >= empty.length || y < 0 || y >= empty.length) return;
        empty[x][y] = -1;
    }

    private static boolean checkSmallSizeCell(byte[][] empty, int x, int y) {
        if (x < 0 || x >= empty.length || y < 0 || y >= empty.length) return true;
        if (isOccupied(1, empty[x][y])) return false;
        return true;
    }

    public static SizeClass floatToSize(float progress) {
        if (progress < 0.2f) return SizeClass.NONE;
        if (progress < 0.33f) return SizeClass.SMALL;
        if (progress < 0.66f) return SizeClass.MEDIUM;
        return SizeClass.LARGE;
    }

    public static SizeClass trimSize(SizeClass size, SizeClass maxSize) {
        if (maxSize.toInt() < size.toInt()) return maxSize;
        return size;
    }

    public static SizeClass intToSize(int size) {
        if (size == 0) return SizeClass.NONE;
        if (size == 1) return SizeClass.SMALL;
        if (size == 2) return SizeClass.MEDIUM;
        return SizeClass.LARGE;
    }
}
