package com.thebeyond.common.fluid;

import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
    }

    @Override
    protected void randomTick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        super.randomTick(pState, pLevel, pPos, pRandom);

        if (pLevel.isRaining() && pRandom.nextInt(50) == 0){
            pLevel.playSound(null, pPos, SoundEvents.ALLAY_DEATH, SoundSource.AMBIENT);
            pos = new Vec3(pPos.getX() + pRandom.nextFloat(), pPos.getY(), pPos.getZ() + pRandom.nextFloat());
            if(pLevel.getBlockState(pPos.relative(Direction.UP)).isAir()) addParticle(BeyondParticleTypes.GLOP.get(), pos, pLevel);
        }

        //if(pState.getValue(LEVEL)==8){
        //    pLevel.playSound(null, pPos, SoundEvents.ALLAY_DEATH, SoundSource.AMBIENT);
        //    pLevel.setBlock(pPos, Blocks.AIR.defaultBlockState(), 3);
        //    Vec3 pos = Vec3.ZERO;
        //    if(!pLevel.isClientSide)
        //        for(int i=0; i<pRandom.nextInt(20); i++){
        //            pos = new Vec3(pPos.getX() + pRandom.nextFloat(), pPos.getY() + pRandom.nextFloat(), pPos.getZ() + pRandom.nextFloat());
        //            addParticle(BeyondParticleTypes.GLOP.get(), pos, pLevel);
        //        }
        //}
    }

    private static void addParticle(SimpleParticleType pParticleType, Vec3 pPos, Level pLevel) {
        pLevel.addParticle(pParticleType, pPos.x()+0.5F, pPos.y()+1F, pPos.z()+0.5F, 0.0, 0.0, 0.0);
    }
}
