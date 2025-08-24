package com.thebeyond.common.fluids;

import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
        //if(pState.getValue(LEVEL)==0)
        //    return RenderShape.MODEL;
        return RenderShape.INVISIBLE;
    }

    @Override
    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        super.animateTick(pState, pLevel, pPos, pRandom);
        Vec3 pos = Vec3.ZERO;
        if (pRandom.nextInt(10) == 0){
            pos = new Vec3(pPos.getX() + pRandom.nextFloat(), pPos.getY(), pPos.getZ() + pRandom.nextFloat());
            if(pLevel.getBlockState(pPos.relative(Direction.UP)).isAir()) addParticle(BeyondParticleTypes.GLOP.get(), pos, pLevel);
        }
    }

    private static void addParticle(SimpleParticleType pParticleType, Vec3 pPos, Level pLevel) {
        pLevel.addParticle(pParticleType, pPos.x()+0.5F, pPos.y()+1F, pPos.z()+0.5F, 0.0, 0.0, 0.0);
    }
}
