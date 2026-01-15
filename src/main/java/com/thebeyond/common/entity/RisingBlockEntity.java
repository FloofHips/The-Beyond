package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.mixin.FallingBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

import static com.ibm.icu.text.PluralRules.Operand.f;

public class RisingBlockEntity extends FallingBlockEntity {
    private int fallDamageMax;
    private float fallDamagePerDistance;
    public RisingBlockEntity(EntityType<? extends RisingBlockEntity> entityType, Level level) {
        super(entityType, level);
    }
    private RisingBlockEntity(Level level, double x, double y, double z, BlockState state) {
        this(BeyondEntityTypes.RISING_BLOCK.get(), level);
        this.blocksBuilding = true;
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());

        ((FallingBlockEntityAccessor) this).setBlockState(state);
    }
    protected double getDefaultGravity() {
        return -0.04;
    }

    @Override
    public void tick() {
        super.tick();

        Predicate<Entity> predicate = EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE);
        List<Entity> entities = this.level().getEntities(this, this.getBoundingBox(), predicate);

        if (!entities.isEmpty()) this.causeFallDamage(this.blockPosition().getY() - this.getStartPos().getY(), 0.0f, this.damageSources().fall());

    }

    public void setHurtsEntities(float fallDamagePerDistance, int fallDamageMax) {
        this.fallDamagePerDistance = fallDamagePerDistance;
        this.fallDamageMax = fallDamageMax;
        super.setHurtsEntities(fallDamagePerDistance, fallDamageMax);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        int i = Mth.ceil(fallDistance - 1.0F);
        if (i < 0)
            return false;

        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    public boolean onGround() {
        if (this.blockPosition().above().getY() > level().getMaxBuildHeight() + 20) return true;
        if (!level().getBlockState(this.blockPosition().above()).isAir()) return true;
        return super.onGround();
    }

    public static RisingBlockEntity rise(Level level, BlockPos pos, BlockState blockState) {
        RisingBlockEntity risingBlockEntity = new RisingBlockEntity(level, (double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5, blockState.hasProperty(BlockStateProperties.WATERLOGGED) ? (BlockState)blockState.setValue(BlockStateProperties.WATERLOGGED, false) : blockState);
        level.setBlock(pos, blockState.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(risingBlockEntity);
        return risingBlockEntity;
    }
}
