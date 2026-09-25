package com.thebeyond.common.entity;

import com.thebeyond.client.particle.BellowJetOptions;
import com.thebeyond.client.particle.CircleColorTransitionOptions;
import com.thebeyond.client.particle.CloudColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.entity.util.SlowRotFlyingMoveControl;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.util.AOEManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.joml.Vector3f;

public class BrubbleEntity extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> STANDING = SynchedEntityData.defineId(BrubbleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_ROCKET = SynchedEntityData.defineId(BrubbleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_SULKING = SynchedEntityData.defineId(BrubbleEntity.class, EntityDataSerializers.BOOLEAN);
    private int sitCooldown = 0;
    public BrubbleEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new SlowRotFlyingMoveControl(this, 10, false);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }
    protected PathNavigation createNavigation(Level level) {
        if (hasRocket()) {
            FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);
            return flyingpathnavigation;
        } else {
            return new GroundPathNavigation(this, level());
        }
    }

    public void setStanding(boolean i) {entityData.set(STANDING, i);}
    public boolean isStanding() {return entityData.get(STANDING);}

    public void setRocket(boolean i) {entityData.set(HAS_ROCKET, i);}
    public boolean hasRocket() {return entityData.get(HAS_ROCKET);}

    public void setSulking(boolean i) {entityData.set(IS_SULKING, i);}
    public boolean isSulking() {return entityData.get(IS_SULKING);}

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.ATTACK_KNOCKBACK, 2).add(Attributes.FLYING_SPEED, 0.8).add(Attributes.MOVEMENT_SPEED, 0.2f);
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STANDING, false);
        builder.define(IS_SULKING, false);
        builder.define(HAS_ROCKET, true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Standing", isStanding());
        tag.putBoolean("Rocket", hasRocket());
        tag.putBoolean("Sulking", isSulking());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setStanding(tag.getBoolean("Standing"));
        setRocket(tag.getBoolean("Rocket"));
        setSulking(tag.getBoolean("Sulking"));
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomFlyingGoal(this, 1){
            @Override
            public boolean canUse() {
                if (!hasRocket()) return false;
                return super.canUse();
            }
        });this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1){
            @Override
            public boolean canUse() {
                if (hasRocket()) return false;
                if (!isStanding()) return false;
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
            if (this.tickCount == 1) {
                this.navigation = createNavigation(level());
            }
            if (this.tickCount % 300 == 0) {
                if (!isSulking() && !hasRocket() && !isStanding() && level().random.nextBoolean()) {
                    setStanding(true);
                }
            }
            if (this.tickCount % 100 == 0) {
                if (isSulking() && level().random.nextBoolean()) {//
                    setSulking(false);
                    setStanding(false);
                    serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, position().x, position().y+1, position().z, random.nextInt(2,4), 0.1, 1, 0.1, 0.01);
                    level().playSound(this, this.blockPosition(), SoundEvents.COW_AMBIENT, SoundSource.HOSTILE, 1, 2);
                    this.navigation.stop();
                }

                if (getTarget() != null) {
                    if (hasRocket()) {
                        serverLevel.sendParticles(new BellowJetOptions(20), position.x, position.y + 0.5f, position.z, 20, 0.5, 0.5, 0.5, 0.01);
                        serverLevel.sendParticles(new CircleColorTransitionOptions(
                                new Vector3f(0.3f, 0.6f, 0.8f),
                                new Vector3f(1.0f, 1.0f, 1.0f),
                                0.5f
                        ), position.x+0.5, position.y+0.5, position.z+0.5, 1,0,0,0,1);
                        serverLevel.sendParticles(new SmokeColorTransitionOptions(
                                new Vector3f(0.2f, 0.1f, 0.2f),
                                new Vector3f(0.0f, 0.0f, 0.0f),
                                2f
                        ), position.x+0.5, position.y+0.5, position.z+0.5, 10,0,0,0,0.01);
                        this.setDeltaMovement(getDeltaMovement().add(getTarget().position().subtract(position).normalize()));
                    }

                    this.lookAt(getTarget(), 180, 180);
                }
            } else {
                if (hasRocket()) {
                    if ((getTarget() != null)) {
                        angle += 15.0F * ((float) Math.PI / 180F);
                        Vec3 moveTargetPoint = getTarget().getEyePosition().add((5 * Mth.cos(angle)), (-4.0F + -4.0F + random.nextFloat() * 9.0F), (5 * Mth.sin(angle)));

                        navigation.moveTo(moveTargetPoint.x, moveTargetPoint.y, moveTargetPoint.z, 0.7);
                        this.lookAt(getTarget(), 180, 180);
                    }
                }
            }
            if (hasRocket()) {
                double movement = getDeltaMovement().length();
                if (sitCooldown>0) sitCooldown--;
                if (!isStanding() && movement < 0.15 && sitCooldown == 0) setStanding(true);
                if (isStanding() && movement > 0.15) {
                    setStanding(false);
                    sitCooldown = 40;
                }
                if (hasRocket() && movement > 0.15) {
                    Vec3 smokePos = position.add(getLookAngle().scale(-1));
                    serverLevel.sendParticles(new BellowJetOptions(15), smokePos.x, smokePos.y + 0.5f, smokePos.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }
    }

    public void playerTouch(Player livingEntity) {
        if (!hasRocket()) {
            super.playerTouch(livingEntity);
            return;
        }

        if (level().getDifficulty() != Difficulty.PEACEFUL && !livingEntity.isCreative() && !livingEntity.isSpectator() && this.isAlive() && this.isWithinMeleeAttackRange(livingEntity) && this.hasLineOfSight(livingEntity)) {
            DamageSource damagesource = this.damageSources().mobAttack(this);
            if (livingEntity.hurt(damagesource, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                Level var4 = this.level();
                if (var4 instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel)var4;
                    EnchantmentHelper.doPostAttackEffects(serverlevel, livingEntity, damagesource);
                }
            }
            this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.setDeltaMovement(getDeltaMovement().add(livingEntity.position().subtract(this.position()).normalize().scale(-0.5f)));
        }
        super.playerTouch(livingEntity);
    }

    protected void blockedByShield(LivingEntity entity) {
        if (hasRocket()) {
            disable();
        }

        this.playSound(SoundEvents.RAVAGER_STUNNED, 1.0F, 1.0F);
        entity.push(this);
        entity.hurtMarked = true;
        super.blockedByShield(entity);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity) {
            ItemStack weapon = source.getWeaponItem();
            if (weapon != null && weapon.is(ItemTags.PICKAXES)) {
                disable();
            }
        }

        return super.hurt(source, amount);
    }

    public void disable() {
        if (!hasRocket()) return;
        setRocket(false);
        setRocket(false);
        setStanding(true);
        setSulking(true);
        this.resetFallDistance();
        this.setDeltaMovement(getDeltaMovement().add(0,0.1,0));
        setNoGravity(false);
        this.navigation = new GroundPathNavigation(this, level());
        this.navigation.stop();
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

        return aabb.inflate(1.2f, 1.2f, 1.2f);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        super.checkFallDamage(0, onGround, state, pos);
    }
}
