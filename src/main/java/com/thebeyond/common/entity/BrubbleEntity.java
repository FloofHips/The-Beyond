package com.thebeyond.common.entity;

import com.thebeyond.client.particle.BellowJetOptions;
import com.thebeyond.client.particle.CircleColorTransitionOptions;
import com.thebeyond.common.entity.util.SlowRotFlyingMoveControl;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.util.AOEManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class BrubbleEntity extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> DATA_FLOAT = SynchedEntityData.defineId(BrubbleEntity.class, EntityDataSerializers.BOOLEAN);

    public BrubbleEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new SlowRotFlyingMoveControl(this, 10, false);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);
        flyingpathnavigation.setCanFloat(true);
        return flyingpathnavigation;
    }

    public void setFloating(boolean i) {
        entityData.set(DATA_FLOAT, i);
    }
    public boolean isFloating() {
        return entityData.get(DATA_FLOAT);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.ATTACK_DAMAGE, 1.5).add(Attributes.ATTACK_KNOCKBACK, 2).add(Attributes.FLYING_SPEED, 0.8);
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLOAT, true);
    }

    public void toggleFloat() {
        if (isFloating()) {
            setFloating(false);
            this.resetFallDistance();
            setNoGravity(false);
        } else {
            setFloating(true);
            AOEManager.brubbleKnockback(level(), this);
            setNoGravity(true);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Floating", isFloating());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFloating(tag.getBoolean("Floating"));
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomFlyingGoal(this, 1){
            @Override
            public boolean canUse() {
                if (getTarget()!=null) return false;
                if (!isFloating()) return false;
                return super.canUse();
            }
        });
        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        float angle = 0;
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 position = position();
            //if (this.tickCount % 260 == 0) {
            //    toggleFloat();
            //}

            if (this.tickCount == 1) {
                setFloating(true);
                setNoGravity(true);
            }

            if (isFloating()) serverLevel.sendParticles(new BellowJetOptions(15), position.x, position.y + 0.5f, position.z, 1, 0.1, 0.1, 0.1, 0.01);

            if (this.tickCount % 100 == 0) {
                if (getTarget() != null) {
                    if (isFloating()) {
                        serverLevel.sendParticles(new BellowJetOptions(20), position.x, position.y + 0.5f, position.z, 20, 0.5, 0.5, 0.5, 0.01);
                        serverLevel.sendParticles(new CircleColorTransitionOptions(
                                new Vector3f(0.3f, 0.6f, 0.8f),
                                new Vector3f(1.0f, 1.0f, 1.0f),
                                0.5f
                        ), position.x+0.5, position.y+0.5, position.z+0.5, 1,0,0,0,1);
                    }
                    this.setDeltaMovement(getDeltaMovement().add(getTarget().position().subtract(position).normalize()));
                    this.lookAt(getTarget(), 180, 180);
                }
            } else {
                if (isFloating()) {
                    if ((getTarget() != null)) {
                        angle += 15.0F * ((float) Math.PI / 180F);
                        Vec3 moveTargetPoint = getTarget().getEyePosition().add((5 * Mth.cos(angle)), (-4.0F + -4.0F + random.nextFloat() * 9.0F), (5 * Mth.sin(angle)));

                        navigation.moveTo(moveTargetPoint.x, moveTargetPoint.y, moveTargetPoint.z, 0.7);
                        this.lookAt(getTarget(), 180, 180);
                    }
                }
            }
        }
    }

    public void playerTouch(Player livingEntity) {
        if (level().getDifficulty() != Difficulty.PEACEFUL && this.isAlive() && this.isWithinMeleeAttackRange(livingEntity) && this.hasLineOfSight(livingEntity)) {
            DamageSource damagesource = this.damageSources().mobAttack(this);
            if (!livingEntity.isBlocking() && livingEntity.hurt(damagesource, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                Level var4 = this.level();
                if (var4 instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel)var4;
                    EnchantmentHelper.doPostAttackEffects(serverlevel, livingEntity, damagesource);
                }
            }
            this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.setDeltaMovement(getDeltaMovement().add(livingEntity.position().subtract(this.position()).normalize().scale(-0.5f)));
        }
    }

    protected void blockedByShield(LivingEntity entity) {
        if (isFloating()) {
            setFloating(false);
            this.resetFallDistance();
            setNoGravity(false);
        }

        this.playSound(SoundEvents.RAVAGER_STUNNED, 1.0F, 1.0F);
        this.level().broadcastEntityEvent(this, (byte)39);
        entity.push(this);
        entity.hurtMarked = true;
    }

    protected AABB getAttackBoundingBox() {
        Entity entity = this.getVehicle();
        AABB aabb;
        if (entity != null) {
            AABB aabb1 = entity.getBoundingBox();
            AABB aabb2 = this.getBoundingBox();
            aabb = new AABB(Math.min(aabb2.minX, aabb1.minX), aabb2.minY, Math.min(aabb2.minZ, aabb1.minZ), Math.max(aabb2.maxX, aabb1.maxX), aabb2.maxY, Math.max(aabb2.maxZ, aabb1.maxZ));
        } else {
            aabb = this.getBoundingBox();
        }

        return aabb.inflate(1.5f, 1.0f, 1.5f);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        super.checkFallDamage(0, onGround, state, pos);
    }
}
