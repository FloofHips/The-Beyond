package com.thebeyond.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public class LanternEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(LanternEntity.class, EntityDataSerializers.INT);;
    public LanternEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.entityData.set(SIZE, this.getRandom().nextInt(2));
        this.noPhysics = true;
        this.moveControl = new FlyingMoveControl(this, 20, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.FLYING_SPEED, 0.6).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 48.0);
    }
    protected void registerGoals() {
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(0, new FindFireGoal(this));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);
        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(true);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SIZE, 0);
    }

    public int getSize() {
        return this.entityData.get(SIZE);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount % 100 == 0)
            checkInsideBlocks();
        //if(!isNoGravity()) {
        //    this.setNoGravity(true);
        //    this.noPhysics = true;
        //}

    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        if (!state.isAir())
            this.setDeltaMovement(getDeltaMovement().add(0, (this.random.nextBoolean() ? -1 : 1) * 0.1, 0));
        if (state.is(Blocks.SOUL_FIRE))
            this.setDeltaMovement(getViewVector(0).scale(-0.1f));
    }

    static class FindFireGoal extends MoveToBlockGoal {
        private final LanternEntity lantern;

        private int stareTick = 0;

        public FindFireGoal(LanternEntity lantern) {
            super(lantern, 0.699999988079071, 16);
            this.lantern = lantern;
        }

        public boolean canUse() {
            //if (this.stareTick > 0) return false;
            //if (this.nextStartTick <= 0) {
            //    if (!EventHooks.canEntityGrief(this.lantern.level(), this.lantern)) {
            //        return false;
            //    }
//
            //    this.canRaid = false;
            //    this.wantsToRaid = this.rabbit.wantsMoreFood();
            //}

            return super.canUse();
        }

        public boolean canContinueToUse() {
            if (stareTick == 1)
                return false;
             return super.canContinueToUse();
        }

        public void tick() {
            super.tick();
            if (stareTick > 0) {
                stareTick--;
            }

            this.lantern.getLookControl().setLookAt((double)this.blockPos.getX() + 0.5, (double)(this.blockPos.getY() + 1), (double)this.blockPos.getZ() + 0.5, 10.0F, (float)this.lantern.getMaxHeadXRot());
            if (this.isReachedTarget()) {
                stareTick = 100;
            }

        }

        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockState blockstate = level.getBlockState(pos);
            return blockstate.getBlock() instanceof SoulFireBlock;
        }

        @Override
        public void stop() {
            stareTick = 0;
            super.stop();
        }
    }
}
