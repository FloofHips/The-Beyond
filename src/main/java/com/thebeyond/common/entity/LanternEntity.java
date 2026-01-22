package com.thebeyond.common.entity;

import com.thebeyond.common.block.BonfireBlock;
import com.thebeyond.common.entity.util.SlowRotFlyingMoveControl;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.Predicate;

public class LanternEntity extends PathfinderMob implements PlayerRideable {
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(LanternEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ALPHA = SynchedEntityData.defineId(LanternEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(LanternEntity.class, EntityDataSerializers.BOOLEAN);
    public LanternEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.moveControl = new SlowRotFlyingMoveControl(this, 1, false);
        if (!level.isClientSide) {
            this.entityData.set(SIZE, this.random.nextInt(4));
        }
        this.lookControl = new SmoothSwimmingLookControl(this, 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.FLYING_SPEED, 0.6).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 48.0);
    }
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new LanternMigrateGoal(this));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                if (isFlying()) return false;
                return super.canUse();
            }
        });
        this.goalSelector.addGoal(4, new WaterAvoidingRandomFlyingGoal(this, 1){
            @Override
            public boolean canUse() {
                if (isFlying()) return false;
                return super.canUse();
            }
        });

        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 10f){
            @Override
            public boolean canUse() {
                if (isFlying()) return false;
                return super.canUse();
            }
        });
        this.goalSelector.addGoal(1, new FindFireGoal(this){
            @Override
            public boolean canUse() {
                if (isFlying()) return false;
                return super.canUse();
            }
        });
        this.goalSelector.addGoal(0, new LanternAvoidEntityGoal(this, Player.class, 8, 1, 1){
            @Override
            public boolean canUse() {
                if (isFlying()) return false;
                return super.canUse();
            }
        });
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.2, (p_336182_) -> {
            return p_336182_.is(Items.SOUL_TORCH);
        }, false));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                if (isFlying()) return false;
                return super.canUse();
            }
        });
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LanternEntity.class, true,
                (entity) -> {
                    if (entity instanceof LanternEntity lantern)
                        return lantern.getSize() == this.getSize() + 1;
                    return false; }) {
            @Override
            public boolean canUse() {
                if (isFlying()) return false;
                return super.canUse();
            }
        });
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
        builder.define(FLYING, false);
        builder.define(SIZE, 0);
        builder.define(ALPHA, 0);
    }
    public boolean isTrusting() {
        return getAlpha() > 0;
    }
    public boolean isFlying() {
        return this.entityData.get(FLYING);
    }
    public void setFlying(boolean flying) {
        this.entityData.set(FLYING, flying);
    }
    public int getSize() {
        return this.entityData.get(SIZE);
    }
    public void setSize(int size) {
        this.entityData.set(SIZE, size);
    }
    public int getAlpha() {
        return this.entityData.get(ALPHA);
    }
    public void setAlpha(int alpha) {
        int finalAlpha = Math.clamp(alpha, 0, 255);
        this.entityData.set(ALPHA, finalAlpha);
    }
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Size", this.getSize());
        compound.putInt("Alpha", this.getAlpha());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setSize(compound.getInt("Size"));
        this.setAlpha(compound.getInt("Alpha"));
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
            case 3: return entitydimensions.scale(1.75f, 1.75f);
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

        if (isFlying()) {
            navigation.stop();
            navigation.moveTo(this.position().x, 197f, this.getZ()-10, 0.7);
        }

        if (!isFlying() && level().isThundering())
            setFlying(true);

        if (tickCount == 10) {
            getDefaultDimensions(Pose.STANDING);
            refreshDimensions();
        }

        if (tickCount % 100 == 0) {
            checkInsideBlocks();
        }

        if ((tickCount + random.nextInt(20)) % 20 == 0) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + this.getBbHeight()/2, this.getZ(), 0, 0.01, 0);
        }

        if (!isFlying() && getTarget() != null && getTarget() instanceof LanternEntity) {
            this.getNavigation().moveTo(getTarget(), 0.7);
        }
    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!level().isClientSide) {
            if (itemstack.is(Items.SOUL_TORCH)) {
                this.setAlpha((int) (getAlpha()+150*random.nextFloat()));
                itemstack.shrink(1);
                player.addItem(new ItemStack(Items.TORCH));
                this.playSound(SoundEvents.DOLPHIN_EAT);
                return InteractionResult.SUCCESS;
            }

            if (itemstack.is(Items.BRUSH)) {
                if (!isTrusting()) {
                    this.spawnAtLocation(new ItemStack(BeyondItems.LANTERN_SHED.get(), 1+random.nextInt(0, getSize()+1)));
                    this.gameEvent(GameEvent.ENTITY_INTERACT);
                    this.playSound(SoundEvents.ARMADILLO_BRUSH);
                    itemstack.hurtAndBreak(16, player, getSlotForHand(hand));
                    this.playSound(SoundEvents.FOX_TELEPORT);
                    ((ServerLevel) this.level()).sendParticles(ParticleTypes.DUST_PLUME, this.getX(), getY(),getZ(), 5 * (getSize()+1), 0.4*(getSize()+1),0.4*(getSize()+1),0.4*(getSize()+1),0.01);

                    this.discard();
                    return InteractionResult.SUCCESS;
                }

                this.spawnAtLocation(new ItemStack(BeyondBlocks.ECTOPLASM.asItem(), 1+random.nextInt(0, (getSize()+1)*2)));
                this.gameEvent(GameEvent.ENTITY_INTERACT);
                this.playSound(SoundEvents.ARMADILLO_BRUSH);
                itemstack.hurtAndBreak(16, player, getSlotForHand(hand));
                ((ServerLevel) this.level()).sendParticles(ParticleTypes.DUST_PLUME, this.getX(), getY(),getZ(), 5 * (getSize()+1), 0.4*(getSize()+1),0.4*(getSize()+1),0.4*(getSize()+1),0.01);

                this.setAlpha((int) (getAlpha()-150*random.nextFloat()));
                if ((getAlpha()/255f) < this.level().random.nextFloat())
                flee(player);
                return InteractionResult.SUCCESS;
            }

            if (getSize() == 3 && isTrusting()) {
                player.startRiding(this);
                return InteractionResult.SUCCESS;
            } else {
                flee(player);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15 * 20, 0));
        return super.getDismountLocationForPassenger(entity);
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    protected void positionRider(Entity entity, MoveFunction moveFunction) {
        moveFunction.accept(entity, this.getX(), this.getY()+2, this.getZ());
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return super.getPassengerAttachmentPoint(entity, dimensions, partialTick);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        if (!state.isAir())
            this.setDeltaMovement(getDeltaMovement().add(0, 0.05, 0));
        if (state.is(Blocks.SOUL_FIRE))
            this.setDeltaMovement(getViewVector(0).scale(-0.01f));
    }

    private void flee(Entity entity) {
        if (entity == null) return;
        Vec3 awayVector = this.position().subtract(entity.position()).normalize();
        if (this.level() instanceof ServerLevel level)
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.DUST_PLUME, this.getX(), this.getY(),this.getZ(), 5 * (this.getSize()+1), 0.4*(this.getSize()+1),0.4*(this.getSize()+1),0.4*(this.getSize()+1),0.01);
        this.lookControl.setLookAt(awayVector);
        this.setDeltaMovement(awayVector);
        if (this.level() instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 0.5, getZ(), 2 * (this.getSize()+1), 0.1, 0.5, 0.1, 0);

    }

    public static boolean checkMonsterSpawnRules(EntityType<LanternEntity> lanternEntityEntityType, ServerLevelAccessor serverLevelAccessor, MobSpawnType mobSpawnType, BlockPos blockPos, RandomSource randomSource) {
        return serverLevelAccessor.getBlockState(blockPos.below()).isAir() && serverLevelAccessor.getBlockState(blockPos.above()).isAir() && serverLevelAccessor.getBlockState(blockPos).isAir();
    }

    public void split(ServerLevel level, Player player) {
        if (getSize() == 0) return;
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX() + 0, this.getY() + 0, this.getZ() + 0, 10, 0.25, 1, 0.25, 0.015);
        spawnChild(level);
        spawnChild(level);
        this.spawnAtLocation(new ItemStack(BeyondItems.LANTERN_SHED.get(), 0+random.nextInt(0, getSize()+2)));
        if (getSize() == 3) spawnTotem(level, player);
        this.discard();
    }

    private void spawnTotem(ServerLevel level, Player player) {
        TotemOfRespiteEntity totem = new TotemOfRespiteEntity(BeyondEntityTypes.TOTEM_OF_RESPITE.get(), level);
        totem.setOwner(player);

        if(level.addFreshEntity(totem)){
            totem.setPos(this.getX() + level.random.nextFloat(), this.getY() + level.random.nextFloat(), this.getZ() + level.random.nextFloat());
            Vec3 direction = totem.position().subtract(this.position()).normalize();
            totem.setDeltaMovement(direction.x, direction.y, direction.z);
            level.sendParticles(ParticleTypes.PORTAL, totem.getX() + 0, totem.getY() + 0, totem.getZ() + 0, 10, 0.25, 1, 0.25, 0.015);
            level.playSound(null, BlockPos.containing(totem.position()), SoundEvents.ALLAY_DEATH, SoundSource.AMBIENT);
        }
    }

    public void spawnChild(ServerLevel level) {
        LanternEntity child = new LanternEntity(BeyondEntityTypes.LANTERN.get(), level);
        child.setPos(this.getX() + level.random.nextFloat(), this.getY() + level.random.nextFloat(), this.getZ() + level.random.nextFloat());
        child.setSize(this.getSize()-1);
        child.setAlpha(255);

        if(level.addFreshEntity(child)){
            Vec3 direction = child.position().subtract(this.position()).normalize().scale(0.4f);
            child.setDeltaMovement(direction.x, direction.y, direction.z);
            level.sendParticles(ParticleTypes.PORTAL, child.getX() + 0, child.getY() + 0, child.getZ() + 0, 10, 0.25, 1, 0.25, 0.015);
            level.playSound(null, BlockPos.containing(child.position()), SoundEvents.ALLAY_DEATH, SoundSource.AMBIENT,5,1);
        }
    }

    public static void spawnSelf(ServerLevel level, BlockPos pos, Player player) {
        LanternEntity lantern = new LanternEntity(BeyondEntityTypes.LANTERN.get(), level);
        lantern.setPos(pos.getX() + level.random.nextFloat(), pos.getY() + level.random.nextFloat(), pos.getZ() + level.random.nextFloat());
        lantern.setSize(level.random.nextInt(4));
        lantern.setAlpha(150);

        if(level.addFreshEntity(lantern)){

            lantern.playSound(SoundEvents.FOX_TELEPORT);
            Vec3 direction = lantern.position().subtract(player.position()).normalize().scale(0.8f);
            lantern.setDeltaMovement(direction.x, direction.y, direction.z);
            lantern.lookAt(player, 180, 180);
            level.sendParticles(ParticleTypes.PORTAL, lantern.getX() + 0, lantern.getY() + 0, lantern.getZ() + 0, 10, 0.25, 1, 0.25, 0.015);
            level.playSound(null, BlockPos.containing(lantern.position()), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.AMBIENT,5,1);
        }
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

    static class LanternMigrateGoal extends Goal {
        private final LanternEntity lantern;

        LanternMigrateGoal(LanternEntity lantern) {
            this.lantern = lantern;
        }

        @Override
        public boolean canUse() {
            return lantern.level().isThundering();
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            super.start();
            lantern.setFlying(true);
            lantern.setDeltaMovement(new Vec3(0, 0, -1).scale(0.05).add(lantern.random.nextFloat()-0.5f, 0.5, lantern.random.nextFloat()-0.5f));
        }

        @Override
        public void stop() {
            super.stop();
            lantern.setFlying(false);
        }

        @Override
        public void tick() {
            super.tick();
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
            this.lantern.flee(this.toAvoid);
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
                    this.lantern.flee(this.toAvoid);
                }

                if (this.lantern.getRandom().nextInt(20) == 0) {
                    addPanicMovement();
                }
            }
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
            super(lantern, 1, 16);
            this.lantern = lantern;
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
            if (blockstate.is(BeyondBlocks.BONFIRE.get())) return blockstate.getValue(BonfireBlock.LIT) != lantern.level().isRaining();
            return false;
        }

        @Override
        public void stop() {
            stareTick = 0;
            super.stop();
        }
    }
}
