package com.thebeyond.common.fluid;

import com.thebeyond.common.entity.EnderglopEntity;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;

public class GellidVoidBlock extends LiquidBlock {
    public GellidVoidBlock(FlowingFluid p_54694_, Properties p_54695_) {
        super(p_54694_, p_54695_);
    }
    @Override
    protected RenderShape getRenderShape(BlockState pState) {
        if(pState.getValue(LEVEL)==0)
          return RenderShape.MODEL;
        return RenderShape.INVISIBLE;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        //if(state.getValue(LEVEL) == 8 && level.getBlockState(currentPos.above()).isAir())
        //    level.setBlock(currentPos.above(), BeyondBlocks.VOID_FLAME.get().defaultBlockState(), 3);
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        super.animateTick(pState, pLevel, pPos, pRandom);
        Vec3 pos = Vec3.ZERO;
        if (pRandom.nextInt(50) == 0){
            pos = new Vec3(pPos.getX() + pRandom.nextFloat(), pPos.getY(), pPos.getZ() + pRandom.nextFloat());
            if(pLevel.getBlockState(pPos.relative(Direction.UP)).isAir()) addParticle(BeyondParticleTypes.GLOP.get(), pos, pLevel);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        entity.setDeltaMovement(entity.getDeltaMovement().add(0, .1, 0));

        if (entity.tickCount % (200 + level.random.nextInt(0, 100)) == 0 && level instanceof ServerLevel serverLevel && entity instanceof LivingEntity livingEntity) {
            if (livingEntity.hasEffect(BeyondEffects.WEIGHTLESS)) {
                MobEffectInstance effect = livingEntity.getEffect(BeyondEffects.WEIGHTLESS);
                if (effect.getAmplifier() <= 3) {
                    serverLevel.sendParticles(ColorUtils.voidOptions, entity.getX() + 0.5, entity.getY() + 0.5, entity.getZ() + 0.5, 7, 0.25, 0.25, 0.25, 0);
                    livingEntity.addEffect(new MobEffectInstance(BeyondEffects.WEIGHTLESS, effect.getDuration(), effect.getAmplifier() + 1));
                }
            } if (entity instanceof EnderglopEntity enderglop) {
                int size = enderglop.getSize();
                if (size <= 5) {
                    serverLevel.sendParticles(ColorUtils.voidOptions, entity.getX() + 0.5, entity.getY() + 0.5, entity.getZ() + 0.5, 7, 0.25, 0.25, 0.25, 0);
                    enderglop.setSize(size + 1, true);
                }
            } if (!livingEntity.hasEffect(BeyondEffects.UNSTABLE)) {
                livingEntity.addEffect(new MobEffectInstance(BeyondEffects.UNSTABLE, 900));
                serverLevel.sendParticles(ParticleTypes.PORTAL, entity.getX() + 0.5, entity.getY() + 0.5, entity.getZ() + 0.5, 7, 0.25, 0.25, 0.25, 0);
            }
        }
    }

    @Override
    protected void randomTick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        super.randomTick(pState, pLevel, pPos, pRandom);

        if (pLevel.isRaining() && pRandom.nextInt(10) == 0){
            pLevel.playSound(null, pPos, SoundEvents.ALLAY_DEATH, SoundSource.AMBIENT);

            if(pLevel.getBlockState(pPos.relative(Direction.UP)).isAir())
                pLevel.sendParticles(ColorUtils.voidOptions, pPos.getX() + 0.5, pPos.getY() + 0.5, pPos.getZ() + 0.5, 15, 0.25, 0.15, 0.25, 0);
        }
    }

    private static void addParticle(SimpleParticleType pParticleType, Vec3 pPos, Level pLevel) {
        pLevel.addParticle(pParticleType, pPos.x()+0.5F, pPos.y()+1F, pPos.z()+0.5F, 0.0, 0.1, 0.0);
    }
}
