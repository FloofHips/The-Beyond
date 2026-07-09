package com.thebeyond.common.block;

import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.client.particle.BellowJetOptions;
import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.network.CameraShootPayload;
import com.thebeyond.common.network.GaussVentParticlePayload;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public class GaussVentBlock extends Block {
    public GaussVentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) return;
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS);
        if (isBig(level, pos)) {
            level.sendParticles(new SmokeColorTransitionOptions(
                    new Vector3f(0.9f, 0.75f, 0.9f),
                    new Vector3f(1f, 1f, 1f),
                    2.5f
            ), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.02);
            BellowBlock.serverPush(level, pos, state, 15, 40, Direction.UP);
        } else {
            level.sendParticles(new SmokeColorTransitionOptions(
                    new Vector3f(0.9f, 0.75f, 0.9f),
                    new Vector3f(1f, 1f, 1f),
                    1.5f
            ), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 15, 0.1, 0.1, 0.1, 0.01);
            BellowBlock.serverPush(level, pos, state, 15, 15, Direction.UP);
        }
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) return;

        if (random.nextInt(10) < 2) level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS);

        if (isBig(level, pos)) {
            PacketDistributor.sendToPlayersNear(level, null, pos.getX(), pos.getY(), pos.getZ(), 64, new GaussVentParticlePayload(pos, true));
            BellowBlock.serverPush(level, pos, state, 15, 40, Direction.UP);
        } else {
            PacketDistributor.sendToPlayersNear(level, null, pos.getX(), pos.getY(), pos.getZ(), 32, new GaussVentParticlePayload(pos, false));
            BellowBlock.serverPush(level, pos, state, 15, 15, Direction.UP);
        }

        if (random.nextInt(100) > 1) level.scheduleTick(pos, this, 1);
        else {
            level.sendParticles(new PixelColorTransitionOptions(
                    new Vector3f(0.9f, 0.75f, 0.9f),
                    new Vector3f(1f, 1f, 1f),
                    0.5f
            ), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 15, 0.1, 0.1, 0.1, 0.005);
        }
    }

    public boolean isBig(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (d.getAxis().isVertical()) continue;
            if (!level.getBlockState(pos.relative(d)).is(BeyondBlocks.GAUSS_VENT.get())) return false;
        }
        return true;
    }
}
