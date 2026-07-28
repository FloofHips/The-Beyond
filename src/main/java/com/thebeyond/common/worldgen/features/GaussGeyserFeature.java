package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import team.chisel.ctm.client.util.Dir;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.world.level.block.MultifaceBlock.canAttachTo;
import static net.minecraft.world.level.block.MultifaceBlock.hasFace;

public class GaussGeyserFeature extends Feature<NoneFeatureConfiguration> {
    public GaussGeyserFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    List<BlockPos> zymotePos = new ArrayList<>();
    boolean old = false;

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource source = context.random();
        SimplexNoise noise = new SimplexNoise(source);

        int size = getSize(level, source, origin);
        if (!canPlace(size)) return false;

        int radius = Math.max(2, size + source.nextInt(1, 2));
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos blockPos = origin.offset(x, y, z);
                    double distedSqr = blockPos.distSqr(origin);
                    if (distedSqr <= radius * radius) {
                        if (level.getBlockState(blockPos).is(BeyondTags.END_DECORATOR_REPLACEABLE) && noise.getValue(x*0.1f, y, z*0.1f) < 0.5f) {
                            level.setBlock(blockPos, BeyondBlocks.GAUSSANITE.get().defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        int height = size + source.nextInt(5, 10);
        old = source.nextBoolean() || size==0;

        if (size==0) build(level, source, height, origin);
        else generateSection(level, origin, size, Math.max(0, size-source.nextInt(2,4)), height,true, source);
        //else generateSection(level, origin, 0, 0, height,true, source);

        if (old) {
            spreadZymote(level, source);
            spreadZymoteEdges(level, source);

            zymotePos.clear();
        }

        return true;
    }

    private void spreadZymote(WorldGenLevel level, RandomSource random) {
        List<BlockPos> currentZymotes = new ArrayList<>(zymotePos);

        for (BlockPos pos : currentZymotes) {
           for (Direction d : Direction.values()) {
               BlockPos blockPos = pos.relative(d);
               if (random.nextBoolean() && level.getBlockState(blockPos).isSolid()) {
                   level.setBlock(blockPos, BeyondBlocks.ZYMOTE.get().defaultBlockState(), 2);
                   zymotePos.add(blockPos);
               }
           }
        }
    }

    private void spreadZymoteEdges(WorldGenLevel level, RandomSource random) {
        List<BlockPos> currentZymotes = new ArrayList<>(zymotePos);

        for (BlockPos pos : currentZymotes) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (random.nextBoolean()) continue;
                        BlockPos targetPos = pos.offset(dx, dy, dz);

                        if (!level.getBlockState(targetPos).isAir()) continue;
                        Direction attachmentFace = null;
                        boolean flag = false;
                        for (Direction d : Direction.values()) {
                            if (level.getBlockState(targetPos.relative(d)).is(BeyondBlocks.ZYMOTE.get())) {
                                flag = true;
                            }
                            if (level.getBlockState(targetPos.relative(d)).isSolid()) {
                                attachmentFace = d;
                            }
                        }

                        if (attachmentFace == null) continue;

                        if (!flag) level.setBlock(targetPos, BeyondBlocks.CREEPING_ZYMOTE.get().defaultBlockState().setValue(GlowLichenBlock.getFaceProperty(attachmentFace), true), 2);
                    }
                }
            }
        }
    }


    public void build(WorldGenLevel level, RandomSource random, int size, BlockPos pos) {
        for (int y = 0; y <= size; y++) {

            BlockState state = Blocks.END_STONE.defaultBlockState();
            if (y >= (size/3)*2 || y == size/2 || y == size/3)
                state = BeyondBlocks.GAUSSANITE.get().defaultBlockState();

            level.setBlock(pos.offset(0, y, 0), state, 2);
        }
        setVent(level, pos.offset(0, size, 0));
    }

    private void generateSection(WorldGenLevel level, BlockPos blockpos, int baseDiam, int topDiam, int height, boolean upward, RandomSource randomsource) {

        int f = baseDiam*2;
        int d = topDiam*2;
        int lastHeight = -1000;
        int xBias = randomsource.nextBoolean() ? 1 : -1;
        int zBias = randomsource.nextBoolean() ? 1 : -1;

        for(int i = 0; i <= height; ++i) {
            for(int j = -f; j <= f; ++j) {
                for(int k = -f; k <= f; ++k) {

                    float progress = ((float)i / height);
                    int workingDiam = (int) Mth.lerp(progress, f, d);
                    int currentDiam = workingDiam==4 ? 3 : workingDiam;

                    if ((float)(j * j + k * k) <= currentDiam) {

                        BlockState state = (i >= ((height/3))*2 || i == (height/2) || i == (height/3)) ? BeyondBlocks.GAUSSANITE.get().defaultBlockState() : Blocks.END_STONE.defaultBlockState();

                        this.setBlock(level, blockpos.offset(j, i, k), state);

                        if (i==0 || i==1)
                            this.setBlock(level, blockpos.offset(j, i-((i*2)+1), k), state);

                        lastHeight = i;
                        if (currentDiam==1)
                            level.setBlock(blockpos.offset(xBias,i, zBias), state, 2);
                        if (currentDiam==0) {
                            level.setBlock(blockpos.offset(j,i, k+zBias), state, 2);
                            level.setBlock(blockpos.offset(j+xBias,i,k), state, 2);
                            level.setBlock(blockpos.offset(j+xBias,i, k+zBias), state, 2);
                        }

                        if (old && (randomsource.nextInt(20)<2) && (state.is(Blocks.END_STONE) || i >= (height/3)*2)) {
                            this.setBlock(level, blockpos.offset(j, i, k), BeyondBlocks.ZYMOTE.get().defaultBlockState());
                            zymotePos.add(blockpos.offset(j, i, k));
                        }
                    }
                }
            }
        }

        if (lastHeight == -1000) return;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x!=0 && z!=0 && randomsource.nextBoolean()) continue;
                if (level.getBlockState(blockpos.offset(x, lastHeight, z)).is(BeyondBlocks.GAUSSANITE.get())) {
                  setVent(level, blockpos.offset(x, height, z));
                }
            }
        }

        //BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        //int xBias = random.nextBoolean() ? 1 : -1;
        //int zBias = random.nextBoolean() ? 1 : -1;
//
        //for (int y = 0; y < height; y++) {
        //    float progress = ((float)y / height)*1.5F;
        //    int currentDiam = (int) Mth.lerp(progress, baseDiam, topDiam);
        //    int radius = currentDiam / 2;
//
        //    int yPos = upward ? basePos.getY() + y : basePos.getY() - y;
//
        //    BlockState state = Blocks.END_STONE.defaultBlockState();
        //    if (y >= (height/3)*2 || y == height/2 || y == height/3)
        //        state = BeyondBlocks.GAUSSANITE.get().defaultBlockState();
//
        //    for (int x = -radius; x <= radius; x++) {
        //        for (int z = -radius; z <= radius; z++) {
//
        //            if (radius > 1 && Mth.abs(z) == Mth.abs(x) && Mth.abs(x) == radius) continue;
//
        //            if (old && (random.nextInt(20)<2) && (x == Math.abs(radius) || z == Math.abs(radius)) && (state.is(Blocks.END_STONE) || y >= (height/3)*2)) {
        //                mutablePos.set(basePos.getX() + x, yPos, basePos.getZ() + z);
        //                level.setBlock(mutablePos, BeyondBlocks.ZYMOTE.get().defaultBlockState(), 2);
        //                zymotePos.add(mutablePos.immutable());
//
        //                if (radius==0) {
        //                    level.setBlock(mutablePos.offset(0,0, zBias), state, 2);
        //                    level.setBlock(mutablePos.offset(xBias,0,0), state, 2);
        //                    level.setBlock(mutablePos.offset(xBias,0, zBias), state, 2);
        //                }
//
        //                continue;
        //            }
//
        //            mutablePos.set(basePos.getX() + x, yPos, basePos.getZ() + z);
        //            level.setBlock(mutablePos, state, 2);
        //            if (radius==0) {
        //                level.setBlock(mutablePos.offset(0,0, zBias), state, 2);
        //                level.setBlock(mutablePos.offset(xBias,0,0), state, 2);
        //                level.setBlock(mutablePos.offset(xBias,0, zBias), state, 2);
        //            }
        //        }
        //    }
        //}
//
        //for (int x = -1; x <= 1; x++) {
        //    for (int z = -1; z <= 1; z++) {
        //        if (x!=0 && z!=0 && random.nextBoolean()) continue;
        //        if (level.getBlockState(basePos.offset(x, height-1, z)).is(BeyondBlocks.GAUSSANITE.get())) {
        //            setVent(level, basePos.offset(x, height-1, z));
        //        }
        //    }
        //}
    }

    public void setVent(WorldGenLevel level, BlockPos pos) {
        if (!level.getBlockState(pos.offset(0, 1, 0)).isAir()) return;

        level.setBlock(pos, BeyondBlocks.GAUSS_VENT.get().defaultBlockState(), 2);
        for (Direction d : Direction.values()) {
            if (d.getAxis().isVertical()) continue;
            if (level.getBlockState(pos.offset(d.getStepX(), -1, d.getStepZ())).isAir()) return;
        }
        level.setBlock(pos.offset(0,-1,0), BeyondBlocks.GELLID_VOID.get().defaultBlockState(), 2);
    }

    public int getSize(WorldGenLevel level, RandomSource random, BlockPos pos) {
        int randomSize = random.nextInt(1, 6);
        int maxHeight = 0;
        int requiredBaseHeight = 1;

        for (int i = -randomSize; i <= randomSize; i++) {
            for (int j = -randomSize; j <= randomSize; j++) {
                if (level.getBlockState(pos.offset(i, -1, j)).isAir()) {
                    return -1;
                }
                if (!level.getBlockState(pos.offset(i, 0, j)).isAir()) {
                    return -1;
                }
            }
        }

        for (int i = 1; i <= randomSize + 8; i++) {
            if (level.getBlockState(pos.offset(0, i, 0)).isAir()) {
                maxHeight++;
            } else {
                break;
            }
        }

        if (maxHeight < requiredBaseHeight) {
            return -1;
        }

        return Math.min(random.nextInt(0, maxHeight + 1), random.nextInt(0, maxHeight + 1));
    }

    public boolean canPlace(int size) {
        return size != -1;
    }
}
