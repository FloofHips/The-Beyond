package com.thebeyond.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.apache.logging.log4j.core.appender.rolling.action.IfAll;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Predicate;

public class LanternEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(LanternEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TRUSTING = SynchedEntityData.defineId(LanternEntity.class, EntityDataSerializers.BOOLEAN);
    public LanternEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.moveControl = new FlyingMoveControl(this, 20, false);
    }

    @Override
    public int getMaxHeadYRot() {
        return 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.FLYING_SPEED, 0.6).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 48.0);
    }
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomFlyingGoal(this, 1));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(1, new FindFireGoal(this));
        this.goalSelector.addGoal(0, new LanternAvoidEntityGoal(this, Player.class, 8, 3, 3f));

        this.goalSelector.addGoal(0, new LanternTemptGoal(this, 1.5f, (item) -> {
            return item.is(ItemTags.OCELOT_FOOD);
        } , true));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LanternEntity.class, true,
                (entity) -> {
                    if (entity instanceof LanternEntity lantern)
                        return lantern.getSize() == this.getSize() + 1;
                    return false;
                }));
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
        builder.define(TRUSTING, false);
        builder.define(SIZE, this.random.nextInt(3));
    }
    public boolean isTrusting() {
        return this.entityData.get(TRUSTING);
    }
    private void setTrusting(boolean trust) {
        this.entityData.set(TRUSTING, trust);
    }
    public int getSize() {
        return this.entityData.get(SIZE);
    }
    private void setSize(int size) {
        this.entityData.set(SIZE, size);
    }
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Size", this.getSize());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setSize(compound.getInt("Size"));
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0f;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions entitydimensions = super.getDefaultDimensions(pose);
        switch (getSize()) {
            case 0: return entitydimensions.scale(0.3f, 0.3f);
            case 1: return entitydimensions.scale(0.7f, 0.5f);
            case 2: return entitydimensions.scale(0.7f, 1.5f);
        }
        return entitydimensions.scale(1.0F);
    }
    public AABB createBoundingBox() {
        switch (getSize()) {
            case 0: return new AABB(getX() - 0.15, getY() - 0.15, getZ() - 0.15, getX() + 0.15, getY() + 0.15, getZ() + 0.15);
            case 1: return new AABB(getX() - 0.6, getY() - 0.25, getZ() - 0.6, getX() + 0.6, getY() + 0.25, getZ() + 0.6);
            case 2: return new AABB(getX() - 0.7, getY() - 0.8, getZ() - 0.7, getX() + 0.7, getY() + 0.8, getZ() + 0.7);
        }
        return new AABB(getX() - 0.7, getY() - 0.8, getZ() - 0.7, getX() + 0.7, getY() + 0.8, getZ() + 0.7);
    }

    @Override
    public void tick() {
        super.tick();

        if (tickCount == 10) {
            getDefaultDimensions(Pose.STANDING);refreshDimensions();
        }

        if (tickCount % 100 == 0)
            checkInsideBlocks();

        if ((tickCount + random.nextInt(20)) % 20 == 0)
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + this.getBbHeight()/2, this.getZ(), 0, 0.01, 0);

        if (getTarget() != null && getTarget() instanceof LanternEntity && getSize() < 2) {
            this.getNavigation().moveTo(getTarget(), 0.7);
        }
    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level().isClientSide) {
            this.spawnAtLocation(new ItemStack(Items.ARMADILLO_SCUTE));
            this.gameEvent(GameEvent.ENTITY_INTERACT);
            this.playSound(SoundEvents.ARMADILLO_BRUSH);
            itemstack.hurtAndBreak(16, player, getSlotForHand(hand));
            this.playSound(SoundEvents.FOX_TELEPORT);
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.DUST_PLUME, this.getX(), getY(),getZ(), 5 * (getSize()+1), 0.4*(getSize()+1),0.4*(getSize()+1),0.4*(getSize()+1),0.01);

            this.discard();
        }

        if (itemstack.canPerformAction(ItemAbilities.BRUSH_BRUSH)) {
            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    public boolean brushOff() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        if (!state.isAir())
            this.setDeltaMovement(getDeltaMovement().add(0, 0.01, 0));
        if (state.is(Blocks.SOUL_FIRE))
            this.setDeltaMovement(getViewVector(0).scale(-0.01f));
    }

    static class LanternTemptGoal extends TemptGoal {
        private final LanternEntity lantern;

        public LanternTemptGoal(LanternEntity lantern, double speedModifier, Predicate<ItemStack> items, boolean canScare) {
            super(lantern, speedModifier, items, canScare);
            this.lantern = lantern;
        }

        protected boolean canScare() {
            return super.canScare() && !this.lantern.isTrusting();
        }
    }

    static class LanternAvoidEntityGoal extends Goal {
        private final LanternEntity lantern;
        private final Class<? extends LivingEntity> avoidClass;
        private final float avoidDistance;
        private final double walkSpeedModifier;
        private final double sprintSpeedModifier;

        private LivingEntity toAvoid;
        private int panicTime = 0;

        public LanternAvoidEntityGoal(LanternEntity lantern, Class<? extends LivingEntity> avoidClass, float avoidDistance, double walkSpeedModifier, double sprintSpeedModifier) {
            this.lantern = lantern;
            this.avoidClass = avoidClass;
            this.avoidDistance = avoidDistance;
            this.walkSpeedModifier = walkSpeedModifier;
            this.sprintSpeedModifier = sprintSpeedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.toAvoid = this.lantern.level().getNearestEntity(
                    this.lantern.level().getEntitiesOfClass(this.avoidClass,
                            this.lantern.getBoundingBox().inflate(this.avoidDistance),
                            (entity) -> true),
                    TargetingConditions.forNonCombat().range(this.avoidDistance),
                    this.lantern,
                    this.lantern.getX(),
                    this.lantern.getY(),
                    this.lantern.getZ()
            );

            if (this.toAvoid instanceof Player player) {
                if (player.isSpectator() || player.isCreative())
                    return false;
                if (player.isCrouching() || player.isSteppingCarefully() || player.getKnownMovement().length() < 0.01 || this.lantern.isTrusting())
                    return false;
            }


            return this.toAvoid != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.toAvoid != null &&
                    this.lantern.distanceToSqr(this.toAvoid) < (this.avoidDistance * this.avoidDistance) &&
                    this.panicTime > 0;
        }

        @Override
        public void start() {
            this.panicTime = 60;
            updateFleePath();
        }

        @Override
        public void stop() {
            this.toAvoid = null;
            this.panicTime = 0;
            this.lantern.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.toAvoid != null && this.panicTime > 0) {
                this.panicTime--;

                if (this.lantern.tickCount % 10 == 0) {
                    updateFleePath();
                }

                if (this.lantern.getRandom().nextInt(20) == 0) {
                    addPanicMovement();
                }
            }
        }

        private void updateFleePath() {
            if (this.toAvoid == null) return;
            Vec3 awayVector = this.lantern.position().subtract(this.toAvoid.position()).normalize();
            if (lantern.level() instanceof ServerLevel level)
                ((ServerLevel) lantern.level()).sendParticles(ParticleTypes.DUST_PLUME, lantern.getX(), lantern.getY(),lantern.getZ(), 5 * (lantern.getSize()+1), 0.4*(lantern.getSize()+1),0.4*(lantern.getSize()+1),0.4*(lantern.getSize()+1),0.01);
            this.lantern.lookControl.setLookAt(awayVector);
            this.lantern.setDeltaMovement(awayVector);
        }

        private void addPanicMovement() {
            Vec3 randomMove = new Vec3(
                    (this.lantern.getRandom().nextDouble() - 0.5) * 0.5,
                    (this.lantern.getRandom().nextDouble() - 0.5) * 0.3,
                    (this.lantern.getRandom().nextDouble() - 0.5) * 0.5
            );
            this.lantern.lookControl.setLookAt(randomMove);
            this.lantern.setDeltaMovement(this.lantern.getDeltaMovement().add(randomMove));
        }
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
