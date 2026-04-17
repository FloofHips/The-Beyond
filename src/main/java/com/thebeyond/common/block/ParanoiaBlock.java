package com.thebeyond.common.block;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class ParanoiaBlock extends Block {
    public ParanoiaBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    public void animateTick(BlockState blockState, Level level, BlockPos pos, RandomSource random) {
        if (level.isRaining() && random.nextInt(4) == 0) {
            Direction direction = Direction.getRandom(random);
            if (direction != Direction.UP) {
                BlockPos blockpos = pos.relative(direction);
                BlockState blockstate = level.getBlockState(blockpos);
                if (!blockState.canOcclude() || !blockstate.isFaceSturdy(level, blockpos, direction.getOpposite())) {
                    double d0 = direction.getStepX() == 0 ? random.nextDouble() : 0.5 + (double)direction.getStepX() * 0.6;
                    double d1 = direction.getStepY() == 0 ? random.nextDouble() : 0.5 + (double)direction.getStepY() * 0.6;
                    double d2 = direction.getStepZ() == 0 ? random.nextDouble() : 0.5 + (double)direction.getStepZ() * 0.6;
                    level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, (double)pos.getX() + d0, (double)pos.getY() + d1, (double)pos.getZ() + d2, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState block = level.getBlockState(pos.below());
        // Enadrake-seed spam fix (per Reda's decision): the old gate was an unconditional
        // `isRaining() && isAir()` which placed a sprout on 100% of random ticks during any
        // rain. With 10 Obiroot trees per chunk (count_on_every_layer=10 in peer_lands) and
        // ~30 Paranoia blocks per tree, that was spawning dozens of sprouts per chunk per
        // minute, each of which grew into an Enadrake at 0.5 chance/rtick via
        // ObirootSproutBlock.randomTick -> mass Enadrake swarms + void-crystal glass-break
        // cascades when the mobs collided with Fallable crystals.
        //
        // Reda's call: "much lower during rain, and a little higher than that during thunder"
        //   rain     ->  2% per random tick
        //   thunder  ->  5% per random tick (thunder is a superset of rain in vanilla,
        //               so we check it first)
        //   dry      ->  no spawn (matches old behavior)
        if (block.isAir()) {
            float chance;
            if (level.isThundering()) {
                chance = 0.05f;
            } else if (level.isRaining()) {
                chance = 0.02f;
            } else {
                chance = 0.0f;
            }
            if (chance > 0.0f && random.nextFloat() < chance) {
                level.setBlockAndUpdate(pos.below(), BeyondBlocks.OBIROOT_SPROUT.get().defaultBlockState());
            }
        }

        if(paranoiaChance(level) && !block.isAir()){
            level.playSound(null, pos, block.getSoundType(level, pos.below(), null).getStepSound(), SoundSource.AMBIENT, level.random.nextFloat() * 2, level.random.nextFloat() * 2);
        }
        super.randomTick(state, level, pos, random);
    }

    public boolean paranoiaChance(ServerLevel level) {
        if (level.isThundering()) return true;
        if (level.isRaining()) return level.random.nextBoolean();
        return level.random.nextFloat() < 0.05;
    }
}
