package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import com.google.common.annotations.VisibleForTesting;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Objects;

//armor up TODO: custom particles, de-armoring up, changing up sounds when armored? adding conditions depending on the surrounding blocks
//general TODO: add magnetism
public class EnderglopEntity extends Mob implements Enemy {

    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(EnderglopEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGING_TICKS = SynchedEntityData.defineId(EnderglopEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_CHARGING = SynchedEntityData.defineId(EnderglopEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_ARMORED = SynchedEntityData.defineId(EnderglopEntity.class, EntityDataSerializers.BOOLEAN);

    public float targetSquish;
    public float squish = 0;
    public float oSquish;
    private boolean wasOnGround;

    private int mergeCooldown = 0;

    public EnderglopEntity(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        this.fixupDimensions();
        this.moveControl = new SlimeMoveControl(this);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(1, new SlimeFloatGoal(this));
        this.goalSelector.addGoal(2, new SlimeAttackGoal(this));
        this.goalSelector.addGoal(3, new SlimeRandomDirectionGoal(this));
        this.goalSelector.addGoal(5, new SlimeKeepOnJumpingGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return super.canUse() && !EnderglopEntity.this.isTiny() && !EnderglopEntity.this.getIsCharging();
            }
        });

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, EnderglopEntity.class, true));
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_SIZE, 1);
        builder.define(IS_ARMORED, false);

        builder.define(CHARGING_TICKS, 0);
        builder.define(IS_CHARGING, false);
    }

    @VisibleForTesting
    public void setSize(int size, boolean resetHealth) {
        int i = Mth.clamp(size, 1, 127);
        this.entityData.set(ID_SIZE, i);
        this.reapplyPosition();
        this.refreshDimensions();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(i * i);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2F + 0.1F * (float)i);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(i);
        if (resetHealth) {
            this.setHealth(this.getMaxHealth());
        }

        this.xpReward = i;
    }

    public int getSize() {
        return this.entityData.get(ID_SIZE);
    }

    @Override
    public boolean canAttack(LivingEntity target) {

        if (this.getIsCharging()){
            return false;
        }else if (target instanceof EnderglopEntity otherSlime){
            return this.getSize() == otherSlime.getSize() && this.canForm() && otherSlime.canForm();
        }else {
            return super.canAttack(target) && !this.isTiny();
        }
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Size", this.getSize() - 1);
        compound.putBoolean("wasOnGround", this.wasOnGround);
        compound.putBoolean("isCharged", this.getIsArmored());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        this.setSize(compound.getInt("Size") + 1, false);
        super.readAdditionalSaveData(compound);
        this.wasOnGround = compound.getBoolean("wasOnGround");
        this.setIsArmored(compound.getBoolean("isCharged"));
    }

    public boolean getIsArmored(){
        return this.entityData.get(IS_ARMORED);
    }

    public void setIsArmored(boolean armored){
        this.entityData.set(IS_ARMORED, armored);
    }

    public int getChargingTicks(){
        return this.entityData.get(CHARGING_TICKS);
    }

    public void setChargingTicks(int ticks){
        this.entityData.set(CHARGING_TICKS, ticks);
    }

    public boolean getIsCharging(){
        return this.entityData.get(IS_CHARGING);
    }

    public void setIsCharging(boolean charging){
        this.entityData.set(IS_CHARGING, charging);
    }


    public boolean isTiny() {
        return this.getSize() <= 1;
    }

    protected boolean shouldDespawnInPeaceful() {
        return this.getSize() > 0;
    }

    protected ParticleOptions getParticleType() {
        return BeyondParticleTypes.GLOP.get();
    }

    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.20000000298023224);
    }

    public void jumpFromGround() {
        Vec3 vec3 = this.getDeltaMovement();
        float f = (float)this.getSize() * 0.1F;
        this.setDeltaMovement(vec3.x, this.getJumpPower() + f, vec3.z);
        this.hasImpulse = true;
        CommonHooks.onLivingJump(this);
    }

    protected boolean isDealsDamage() {
        return this.isEffectiveAi();
    }

    protected float getAttackDamage() {
        return (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isTiny() ? SoundEvents.MAGMA_CUBE_HURT_SMALL : SoundEvents.MAGMA_CUBE_HURT;
    }

    protected SoundEvent getDeathSound() {
        return this.isTiny() ? SoundEvents.MAGMA_CUBE_DEATH_SMALL : SoundEvents.MAGMA_CUBE_DEATH;
    }

    protected SoundEvent getSquishSound() {
        return this.isTiny() ? SoundEvents.MAGMA_CUBE_SQUISH_SMALL : SoundEvents.MAGMA_CUBE_SQUISH;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    protected SoundEvent getJumpSound() {
        return SoundEvents.MAGMA_CUBE_JUMP;
    }

    private boolean canForm() {
        return this.isAlive() && mergeCooldown <= 0 && this.getSize() < 3;
    }

    public void tick() {
        this.squish += (this.targetSquish - this.squish) * 0.5F;
        this.oSquish = this.squish;
        super.tick();

        if (this.mergeCooldown>0){
            mergeCooldown--;
        }

        //charging stuff
        if (this.getSize()==3 && this.random.nextInt(100)==0 && !this.getIsArmored() && this.onGround() && !this.isInWater() && !this.getIsCharging()){
            this.setIsCharging(true);
            this.setChargingTicks(40);
        }

        if (this.onGround() && this.getIsCharging() && this.getChargingTicks() > 0){
            this.goalSelector.getAvailableGoals().forEach(WrappedGoal::stop);
            int pastChargeTicks = this.getChargingTicks();
            this.setChargingTicks(pastChargeTicks-1);
            this.setTarget(null);
        }

        if (this.getIsCharging() && this.getChargingTicks()==0){
            this.setIsCharging(false);
            this.armorUp();
            this.goalSelector.getAvailableGoals().forEach(WrappedGoal::start);
        }

        if (this.getIsCharging() && !this.onGround()){
            this.setDeltaMovement(0, -5, 0);
        }

        //squish
        if (this.getIsCharging()){

            this.targetSquish = -0.75F;

        }else {
            if (this.onGround() && !this.wasOnGround) {
                float f = this.getDimensions(this.getPose()).width() * 2.0F;
                float f1 = f / 2.0F;
                if (!this.spawnCustomParticles()) {
                    for(int i = 0; (float)i < f * 16.0F; ++i) {
                        float f2 = this.random.nextFloat() * 6.2831855F;
                        float f3 = this.random.nextFloat() * 0.5F + 0.5F;
                        float f4 = Mth.sin(f2) * f1 * f3;
                        float f5 = Mth.cos(f2) * f1 * f3;
                        this.level().addParticle(this.getParticleType(), this.getX() + (double)f4, this.getY() - 0.1, this.getZ() + (double)f5, 0.0, 0.0, 0.0);
                    }
                }

                this.playSound(this.getSquishSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
                this.targetSquish = -0.5F;
            } else if (!this.onGround() && this.wasOnGround) {
                this.targetSquish = 1.0F;
            }
        }

        this.wasOnGround = this.onGround();
        this.decreaseSquish();
    }

    public void armorUp(){
        this.playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        this.setIsArmored(true);
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide && this.getIsCharging()) {
            for (int i = 0; i < 2; i++) {
                this.level()
                        .addParticle(
                                ParticleTypes.PORTAL,
                                this.getRandomX(0.5),
                                this.getRandomY() - 0.25,
                                this.getRandomZ(0.5),
                                (this.random.nextDouble() - 0.5) * 2.0,
                                -this.random.nextDouble(),
                                (this.random.nextDouble() - 0.5) * 2.0
                        );
            }
        }
        super.aiStep();
    }

    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (ID_SIZE.equals(key)) {
            this.refreshDimensions();
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doWaterSplashEffect();
            }
        }

        super.onSyncedDataUpdated(key);
    }

    public void remove(Entity.RemovalReason reason) {
        int i = this.getSize();
        if (!this.level().isClientSide && i > 1 && this.isDeadOrDying()) {
            Component component = this.getCustomName();
            boolean flag = this.isNoAi();
            float f = this.getDimensions(this.getPose()).width();
            float f1 = f / 2.0F;
            int j = i - 1;
            int k = 2;
            ArrayList<Mob> children = new ArrayList();

            for(int l = 0; l < k; ++l) {
                float f2 = ((float)(l % 2) - 0.5F) * f1;
                float f3 = (-0.5F) * f1;
                EnderglopEntity slime = (EnderglopEntity)this.getType().create(this.level());
                if (slime != null) {
                    if (this.isPersistenceRequired()) {
                        slime.setPersistenceRequired();
                    }

                    slime.setCustomName(component);
                    slime.setNoAi(flag);
                    slime.setInvulnerable(this.isInvulnerable());
                    slime.setSize(j, true);
                    slime.mergeCooldown = 300;
                    slime.moveTo(this.getX() + (double)f2, this.getY() + 0.5, this.getZ() + (double)f3, this.random.nextFloat() * 360.0F, 0.0F);
                    children.add(slime);
                }
            }

            if (!EventHooks.onMobSplit(this, children).isCanceled()) {
                Level var10001 = this.level();
                Objects.requireNonNull(var10001);
                children.forEach(var10001::addFreshEntity);
            }
        }

        super.remove(reason);
    }

    public void push(Entity entity) {
        super.push(entity);
        if (entity instanceof IronGolem && this.isDealsDamage()) {
            this.dealDamage((LivingEntity)entity);
        }

    }

    public void playerTouch(Player entity) {
        if (this.isDealsDamage()) {
            this.dealDamage(entity);
        }

    }

    protected void dealDamage(LivingEntity livingEntity) {
        if (this.isAlive() && this.isWithinMeleeAttackRange(livingEntity) && this.hasLineOfSight(livingEntity)) {
            DamageSource damagesource = this.damageSources().mobAttack(this);
            if (livingEntity.hurt(damagesource, this.getAttackDamage())) {
                this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                Level var4 = this.level();
                if (var4 instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel)var4;
                    EnchantmentHelper.doPostAttackEffects(serverlevel, livingEntity, damagesource);
                }
            }
        }

    }

    protected void decreaseSquish() {
        this.targetSquish *= 0.6F;
    }

    protected int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    protected float getSoundVolume() {
        return 0.4F * (float)this.getSize();
    }

    public int getMaxHeadXRot() {
        return 0;
    }

    protected boolean doPlayJumpSound() {
        return this.getSize() > 0;
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        RandomSource randomsource = level.getRandom();
        int i = randomsource.nextInt(3) + 1;
//        if (i < 2 && randomsource.nextFloat() < 0.5F * difficulty.getSpecialMultiplier()) {
//            ++i;
//        }
//
//        int j = 1 << i;
        this.setSize(i, true);
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    float getSoundPitch() {
        float f = this.isTiny() ? 1.4F : 0.8F;
        return ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * f;
    }

    public EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale((float)this.getSize());
    }

    protected boolean spawnCustomParticles() {
        return false;
    }

    static class SlimeMoveControl extends MoveControl {
        private float yRot;
        private int jumpDelay;
        private final EnderglopEntity slime;
        private boolean isAggressive;

        public SlimeMoveControl(EnderglopEntity slime) {
            super(slime);
            this.slime = slime;
            this.yRot = 180.0F * slime.getYRot() / 3.1415927F;
        }

        public void setDirection(float yRot, boolean aggressive) {
            if (!this.slime.getIsCharging()){
                this.yRot = yRot;
                this.isAggressive = aggressive;
            }
        }

        public void setWantedMovement(double speed) {
            if (!this.slime.getIsCharging()){
                this.speedModifier = speed;
                this.operation = Operation.MOVE_TO;
            }
        }

        public void tick() {
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.yRot, 90.0F));
                this.mob.yHeadRot = this.mob.getYRot();
                this.mob.yBodyRot = this.mob.getYRot();

                if (this.slime.getIsCharging()){
                    this.slime.xxa = 0.0F;
                    this.slime.zza = 0.0F;
                    this.mob.setSpeed(0.0F);
                }else{
                    if (this.operation != Operation.MOVE_TO) {
                        this.mob.setZza(0.0F);
                    } else {
                        this.operation = Operation.WAIT;
                        if (this.mob.onGround()) {
                            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                            if (this.jumpDelay-- <= 0) {
                                this.jumpDelay = this.slime.getJumpDelay();
                                if (this.isAggressive) {
                                    this.jumpDelay /= 3;
                                }

                                this.slime.getJumpControl().jump();
                                if (this.slime.doPlayJumpSound()) {
                                    this.slime.playSound(this.slime.getJumpSound(), this.slime.getSoundVolume(), this.slime.getSoundPitch());
                                }
                            } else {
                                this.slime.xxa = 0.0F;
                                this.slime.zza = 0.0F;
                                this.mob.setSpeed(0.0F);
                            }
                        } else {
                            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                        }
                    }
                }

        }

    }

    static class SlimeFloatGoal extends Goal {
        private final EnderglopEntity slime;

        public SlimeFloatGoal(EnderglopEntity slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
            slime.getNavigation().setCanFloat(true);
        }

        public boolean canUse() {
            return (this.slime.isInWater() || this.slime.isInLava()) && this.slime.getMoveControl() instanceof EnderglopEntity.SlimeMoveControl;
        }

        public boolean requiresUpdateEveryTick() {
            return true;
        }

        public void tick() {
            if (this.slime.getRandom().nextFloat() < 0.8F) {
                this.slime.getJumpControl().jump();
            }

            MoveControl var2 = this.slime.getMoveControl();
            if (var2 instanceof EnderglopEntity.SlimeMoveControl slime$slimemovecontrol) {
                slime$slimemovecontrol.setWantedMovement(1.2);
            }

        }
    }


    static class SlimeRandomDirectionGoal extends Goal {
        private final EnderglopEntity slime;
        private float chosenDegrees;
        private int nextRandomizeTime;

        public SlimeRandomDirectionGoal(EnderglopEntity slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        public boolean canUse() {
            if (this.slime.getIsCharging()){
                return false;
            }else {
                return this.slime.getTarget() == null && (this.slime.onGround() || this.slime.isInWater() || this.slime.isInLava() || this.slime.hasEffect(MobEffects.LEVITATION)) && this.slime.getMoveControl() instanceof EnderglopEntity.SlimeMoveControl;
            }
        }

        public void tick() {
            if (--this.nextRandomizeTime <= 0) {
                this.nextRandomizeTime = this.adjustedTickDelay(40 + this.slime.getRandom().nextInt(60));
                this.chosenDegrees = (float)this.slime.getRandom().nextInt(360);
            }

            MoveControl var2 = this.slime.getMoveControl();
            if (var2 instanceof EnderglopEntity.SlimeMoveControl slime$slimemovecontrol) {
                slime$slimemovecontrol.setDirection(this.chosenDegrees, false);
            }

        }
    }

    static class SlimeKeepOnJumpingGoal extends Goal {
        private final EnderglopEntity slime;

        public SlimeKeepOnJumpingGoal(EnderglopEntity slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        public boolean canUse() {
            return !this.slime.isPassenger() && !this.slime.getIsCharging();
        }

        public void tick() {
            MoveControl var2 = this.slime.getMoveControl();
            if (var2 instanceof EnderglopEntity.SlimeMoveControl slime$slimemovecontrol) {
                slime$slimemovecontrol.setWantedMovement(1.0);
            }

        }
    }

    static class SlimeAttackGoal extends Goal {
        private final EnderglopEntity slime;
        private int growTiredTimer;

        public SlimeAttackGoal(EnderglopEntity slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        public boolean canUse() {
            LivingEntity livingentity = this.slime.getTarget();
            if (livingentity == null || this.slime.getIsCharging()) {
                return false;
            } else {
                return !this.slime.canAttack(livingentity) ? false : this.slime.getMoveControl() instanceof EnderglopEntity.SlimeMoveControl;
            }
        }

        public void start() {
            this.growTiredTimer = reducedTickDelay(300);
            super.start();
        }

        public boolean canContinueToUse() {
            LivingEntity livingentity = this.slime.getTarget();
            if (livingentity == null || this.slime.getIsCharging()) {
                return false;
            } else {
                return !this.slime.canAttack(livingentity) ? false : --this.growTiredTimer > 0;
            }
        }

        public boolean requiresUpdateEveryTick() {
            return true;
        }

        public void tick() {
            LivingEntity livingentity = this.slime.getTarget();
            if (livingentity != null) {
                this.slime.lookAt(livingentity, 10.0F, 10.0F);
            }

            MoveControl var3 = this.slime.getMoveControl();
            if (var3 instanceof EnderglopEntity.SlimeMoveControl slime$slimemovecontrol) {
                slime$slimemovecontrol.setDirection(this.slime.getYRot(), this.slime.isDealsDamage());
            }

            if (this.slime.getTarget() instanceof EnderglopEntity otherSlime){
                if (this.slime.distanceTo(otherSlime) <= 0.5F + (this.slime.getBbWidth() + otherSlime.getBbWidth()) / 2D && otherSlime.canForm()) {
                    int glopSize = this.slime.getSize();
                    this.slime.setSize(glopSize+1, true);
                    otherSlime.remove(RemovalReason.DISCARDED);
                    this.slime.playSound(SoundEvents.SLIME_ATTACK);
                    this.slime.mergeCooldown = 600;
                }
            }

        }
    }
}
