package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.common.entity.BrubbleEntity;
import com.thebeyond.common.entity.EnderglopEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.BiConsumer;

public class SootBlock extends FallingBlock implements Fallable {
    public static final MapCodec<SootBlock> CODEC =  simpleCodec(SootBlock::new);
    private static final ExplosionDamageCalculator DAMAGE_CALCULATOR;

    public SootBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isFree(level.getBlockState(pos.above())) && isFree(level.getBlockState(pos.below())) && pos.getY() >= level.getMinBuildHeight()) {
            FallingBlockEntity fallingblockentity = FallingBlockEntity.fall(level, pos, state);

            boolean j = true;

            for (int i = 1; i < 6; i++) {
                if (j && level.getBlockState(pos.offset(0,-i,0)).isAir()) {
                    j = true;
                } else {
                    j = false;
                    break;
                }
            }
            if (j) fallingblockentity.disableDrop();
            this.falling(fallingblockentity);
        }
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return 3284540;
    }

    @Override
    protected void falling(FallingBlockEntity entity) {
        super.falling(entity);
        entity.fallDistance = 3;
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        if (level instanceof ServerLevel serverLevel) {
            BrubbleEntity entity = new BrubbleEntity(BeyondEntityTypes.BRUBBLE.get(), level);
            Vec3 newPos = pos.getCenter();
            entity.setPos(newPos.x, newPos.y, newPos.z);
            serverLevel.addFreshEntity(entity);
        }
        super.onBrokenAfterFall(level, pos, fallingBlock);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.explode(null, Explosion.getDefaultDamageSource(level, null), DAMAGE_CALCULATOR, pos.getX(), pos.getY(), pos.getZ(), 3.0F, false, Level.ExplosionInteraction.TNT);
            serverLevel.sendParticles(ColorUtils.blackOptions, pos.getX(), pos.getY(), pos.getZ(), 10, 0.5, 0.5,0.5, 0.01);
        }
        super.wasExploded(level, pos, explosion);
    }

    static {
        DAMAGE_CALCULATOR = new ExplosionDamageCalculator() {
            public boolean shouldBlockExplode(Explosion p_353087_, BlockGetter p_353096_, BlockPos p_353092_, BlockState p_353086_, float p_353094_) {
                return super.shouldBlockExplode(p_353087_, p_353096_, p_353092_, p_353086_, p_353094_);
            }

            public Optional<Float> getBlockExplosionResistance(Explosion p_353090_, BlockGetter p_353088_, BlockPos p_353091_, BlockState p_353093_, FluidState p_353095_) {
                return super.getBlockExplosionResistance(p_353090_, p_353088_, p_353091_, p_353093_, p_353095_);
            }
        };
    }
}
