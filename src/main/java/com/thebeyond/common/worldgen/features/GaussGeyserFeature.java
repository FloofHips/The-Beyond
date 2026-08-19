package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.BranchBlock;
import com.thebeyond.common.block.ThornsBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.BlockGetter;
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
    List<BlockPos> thornsPos = new ArrayList<>();
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
        else generateSection(level, origin, size, Math.max(0, size-source.nextInt(2,4)), height,true, source, noise);

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

    private void generateSection(WorldGenLevel level, BlockPos blockpos, int baseDiam, int topDiam, int height, boolean upward, RandomSource randomsource, SimplexNoise noise) {

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

        if (level.canSeeSky(blockpos.offset(0, lastHeight, 0))) return;
        generateSoot(level, blockpos.offset(0, lastHeight, 0), randomsource, noise, baseDiam*2);
    }

    private void generateSoot(WorldGenLevel level, BlockPos blockpos, RandomSource randomsource, SimplexNoise noise, int groundRadius) {
        BlockPos.MutableBlockPos start = blockpos.above().mutable();
        boolean brambled = randomsource.nextBoolean();

        while((level.isEmptyBlock(start))) {
            if (level.isOutsideBuildHeight(start)) {
                return;
            }

            if (randomsource.nextBoolean()) groundRadius--;
            start.move(Direction.UP);
        }

        if (groundRadius<=0) return;

        BlockPos.MutableBlockPos ceiling = start;

        for (int x = -groundRadius; x <= groundRadius; x++) {
            for (int y = -groundRadius; y <= 2; y++) {
                for (int z = -groundRadius; z <= groundRadius; z++) {
                    BlockPos blockPos = start.offset(x, y, z);
                    double distedSqr = blockPos.distSqr(start);
                    double noiseValue = noise.getValue(x * 0.1f, y, z * 0.1f);
                    float noisyRadius = (float) (groundRadius-noiseValue*2);

                    if (distedSqr <= noisyRadius * noisyRadius) {
                        if ((level.getBlockState(blockPos).isAir() || level.getBlockState(blockPos).is(BeyondBlocks.BLINDING_THORNS.get())) && level.getBlockState(blockPos.above()).isSolid()) {
                            level.setBlock(blockPos, BeyondBlocks.SOOT_BLOCK.get().defaultBlockState(), 3);
                            if (brambled) brambleUpSoot(level, randomsource, blockPos);
                        }
                    }
                }
            }
        }

        if (brambled) buildClimbingThorns(level, randomsource, ceiling);
        if (brambled) cleanUpBlockstates(level);
    }

    private void placeThorn(WorldGenLevel level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) return;
        this.setBlock(level, pos, BeyondBlocks.BLINDING_THORNS.get().defaultBlockState());
        thornsPos.add(pos);
    }

    private void cleanUpBlockstates(WorldGenLevel level) {
        List<BlockPos> currentThorns = new ArrayList<>(thornsPos);
        for (BlockPos pos : currentThorns) {
            level.setBlock(pos, ThornsBlock.getStateWithConnections(level, pos,BeyondBlocks.BLINDING_THORNS.get().defaultBlockState()), 3);
        }
        thornsPos.clear();
    }

    private boolean buildClimbingThorns(WorldGenLevel level, RandomSource randomsource, BlockPos.MutableBlockPos ceiling) {
        int counter = 0;

        while((!level.isEmptyBlock(ceiling)) && counter < 6) {
            if (level.isOutsideBuildHeight(ceiling)) {
                return false;
            }

            counter++;
            ceiling.move(Direction.UP);
        }

        if (level.isEmptyBlock(ceiling)) {
            for (Direction d : Direction.values()) {
                if (d.getAxis().isVertical()) continue;
                placeThorn(level, ceiling.offset(d.getStepX(), 0,d.getStepZ()));
                level.setBlock(ceiling.offset(d.getStepX(), -1,d.getStepZ()), BeyondBlocks.SOOT_BLOCK.get().defaultBlockState(), 3);
            }

            level.setBlock(ceiling.offset(0, -1,0), BeyondBlocks.SOOT_BLOCK.get().defaultBlockState(), 3);
            for (int i = 0; i < randomsource.nextInt(5, 20); i++) {
                placeThorn(level, ceiling.offset(0, i, 0));
                if (randomsource.nextFloat() < 0.3f) {
                    placeBranch(level, randomsource, ceiling.offset(1, i, 0));
                    placeBranch(level, randomsource, ceiling.offset(-1, i, 0));
                    placeBranch(level, randomsource, ceiling.offset(0, i, -1));
                    placeBranch(level, randomsource, ceiling.offset(0, i, 1));
                }
            }
        }

        return true;
    }

    private void placeBranch(WorldGenLevel level, RandomSource randomsource, BlockPos pos) {
        if (randomsource.nextInt(4) == 0) {
            placeThorn(level, pos);
            if (randomsource.nextBoolean() && level.isEmptyBlock(pos.below())) {
                level.setBlock(pos.below(), BeyondBlocks.SOOT_BLOCK.get().defaultBlockState(),3);
            }
        }
    }

    private void brambleUpSoot(WorldGenLevel level, RandomSource randomsource, BlockPos blockPos) {
        for (Direction d : Direction.values()) {
            BlockPos offset = blockPos.offset(d.getStepX(), d.getStepY(), d.getStepZ());
            if (level.getBlockState(offset).isAir() && randomsource.nextBoolean()) {
                placeThorn(level, offset);

                if (level.getBlockState(offset.above()).isAir()) {
                    placeThorn(level, offset.above());
                }
                if (level.getBlockState(offset.below()).isAir() && randomsource.nextBoolean()) {
                    placeThorn(level, offset.below());
                }
            }
        }
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
