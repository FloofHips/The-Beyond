package com.thebeyond.common.block;

import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class WhistlingBlock extends Block {
    public WhistlingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean raining = level.isRaining();
        if (!raining && random.nextBoolean()) return;
        if (!level.getBlockState(pos.above()).isAir()) return;
        if (!level.getBlockState(pos.above().above()).is(BeyondBlocks.PEARL)) return;

        boolean flag = level.getBlockState(pos.offset(1,1,0)).is(BeyondBlocks.PEARL) && level.getBlockState(pos.offset(-1,1,0)).is(BeyondBlocks.PEARL);
        boolean flag2 = level.getBlockState(pos.offset(0,1,1)).is(BeyondBlocks.PEARL) && level.getBlockState(pos.offset(0,1,-1)).is(BeyondBlocks.PEARL);

        if (flag ^ flag2) {
            if (random.nextBoolean()) level.playLocalSound(pos.getX(), pos.above().getY(), pos.getZ(), BeyondSoundEvents.LANTERN_IDLE.get(), SoundSource.BLOCKS, 2, random.nextFloat() + (raining ? 1 : 0), false);
            if (flag) spawnParticle(level, pos, random, 0, 1 + (raining ? 1 : 0));
            else spawnParticle(level, pos, random, 1 + (raining ? 1 : 0), 0);
        }
    }

    private static void spawnParticle(Level level, BlockPos pos, RandomSource random, int x, int z) {
        Vec3 particlePos = Vec3.atCenterOf(pos.above()).add(-0.3f + random.nextFloat()*0.3f, -0.3f + random.nextFloat()*0.3f, -0.3f + random.nextFloat()*0.3f);
        level.addParticle(new SmokeColorTransitionOptions(
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Vector3f(0.9f, 0.5f, 0.9f),
                2
        ), particlePos.x, particlePos.y, particlePos.z, x*0.1, 0.01f, z*0.1);
    }
}
