package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.NotNull;

public class BleedingThornsFeature extends ThornsFeature {
    public BleedingThornsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    public boolean placeThorns(BlockPos pos, WorldGenLevel level, RandomSource randomsource) {

        int radiusX = randomsource.nextInt(2, 6);
        int radiusY = randomsource.nextInt(1, 4);
        int radiusZ = randomsource.nextInt(2, 6);

        int rx = radiusX * radiusX;
        int ry = radiusY * radiusY;
        int rz = radiusZ * radiusZ;
        int total = rx * ry * rz;

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                for (int y = -radiusY; y <= radiusY; y++) {
                    BlockPos offset = pos.offset(x, y, z);
                    if (randomsource.nextFloat() > 0.3f && ((x * x * ry * rz) + (y * y * rx * rz) + (z * z * rx * ry) <= total) && (level.getBlockState(offset.below()).isSolid() || level.getBlockState(offset.below()).is(getBlockState().getBlock()))) {
                        placeThorn(level, offset);
                    }
                }
            }
        }

        cleanUpBlockstates(level);
        return true;
    }

    @Override
    public @NotNull BlockState getBlockState() {
        return BeyondBlocks.BLEEDING_THORNS.get().defaultBlockState();
    }

    @Override
    public @NotNull BlockState getFloorBlock() {
        return BeyondBlocks.VILET.get().defaultBlockState();
    }
}
