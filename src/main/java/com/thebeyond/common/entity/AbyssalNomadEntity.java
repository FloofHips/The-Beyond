package com.thebeyond.common.entity;

import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.common.entity.util.SlowRotMoveControl;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.util.AOEManager;
import com.thebeyond.util.TeleportUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.FollowTemptation;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumSet;

public class AbyssalNomadEntity extends PathfinderMob {

    private static final byte SIT = 67;
    private static final byte SIT_DOWN = 68;
    private static final byte NOD = 69;
    private static final byte ATTACK = 70;
    private static final byte STAND_UP = 71;

    public final AnimationState sitAnimationState = new AnimationState();
    public final AnimationState sitPoseAnimationState = new AnimationState();
    public final AnimationState standUpAnimationState = new AnimationState();
    public final AnimationState nodAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();
    public BlockPos prayerSite;

    public static final EntityDataAccessor<Boolean> DATA_SITTING = SynchedEntityData.defineId(AbyssalNomadEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> DATA_CORRUPTION = SynchedEntityData.defineId(AbyssalNomadEntity.class, EntityDataSerializers.INT);

    public int sitDownCounter = 0;
    public int attackCounter = 0;

    public boolean isSitting() {
        return this.entityData.get(DATA_SITTING);
    }
    public void setSitting(boolean i) {
        this.entityData.set(DATA_SITTING, i);
    }
    public int getCorruption() {
        return this.entityData.get(DATA_CORRUPTION);
    }
    public void setCorruption(int i) {
        this.entityData.set(DATA_CORRUPTION, i);
    }

    public AbyssalNomadEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new SlowRotMoveControl(this, 20f);
        this.navigation = new NomadNavigation(this, level);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new NomadBodyRotationControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SITTING, false);
        builder.define(DATA_CORRUPTION, 255);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        setSitting(compound.getBoolean("Sitting"));
        setCorruption(compound.getInt("Corruption"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Sitting", entityData.get(DATA_SITTING));
        compound.putInt("Corruption", entityData.get(DATA_CORRUPTION));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (this.level().isClientSide) {
            if (id == NOD) {
                this.nodAnimationState.start(this.tickCount);
                return;
            }
            if (id == ATTACK) {
                this.sitPoseAnimationState.stop();
                this.attackAnimationState.start(this.tickCount);
                return;
            }
            if (id == SIT_DOWN) {
                if (!sitAnimationState.isStarted()) {
                    this.sitAnimationState.start(this.tickCount);
                    return;
                }
            }
            if (id == STAND_UP) {
                if (sitPoseAnimationState.isStarted()) {
                    this.sitPoseAnimationState.stop();
                    this.standUpAnimationState.start(this.tickCount);
                    return;
                }
            }
            if (id == SIT) {
                this.sitAnimationState.stop();
                this.sitPoseAnimationState.start(this.tickCount);
                return;
            }
        }
        super.handleEntityEvent(id);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1) {
            @Override
            public boolean canUse() {
                return !((AbyssalNomadEntity)mob).isSitting() && super.canUse();
            }
        });

        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(0, new attackGoal(this));
        //this.goalSelector.addGoal(0, new goToPrayerSiteGoal(this));
        this.goalSelector.addGoal(0, new PersistentMoveGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 5f));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, EnderglopEntity.class, true));
    }

    public static boolean checkMonsterSpawnRules(EntityType<AbyssalNomadEntity> entityType, ServerLevelAccessor serverLevelAccessor, MobSpawnType mobSpawnType, BlockPos blockPos, RandomSource randomSource) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (getTarget() != null && getTarget() instanceof Player player && (player.isSpectator() || player.isCreative())) setTarget(null);

        handleSit();
        handleAttack();

        if (this.position().y < this.level().getMinBuildHeight() - 5) TeleportUtils.randomTeleport(this.level(), this);
    }

    //private void handlePray() {
    //    if (prayerSite == null) {
    //        if (level() instanceof ServerLevel serverLevel) {
    //            prayerSite = serverLevel.findNearestMapStructure(BeyondTags.NOMAD_PRAYER_SITE, this.blockPosition(), 200, false);
    //        }
    //    } else return;
//
    //    this.navigation.moveTo(prayerSite.getX(), prayerSite.getY(), prayerSite.getZ(), 1);
    //}

    private void handleSit() {
        if (sitDownCounter > 0) {
            sitDownCounter--;

            if(getCorruption() != 0) {
                this.setYHeadRot(getYHeadRot()+(random.nextInt(-50, 50)));
                setCorruption((int) Mth.lerp(random.nextFloat(), Math.min(getCorruption() * 3, 255), 0));
                this.playSound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1, random.nextFloat()*2);
            }

            if (sitDownCounter == 36) {
                setCorruption(0);
                level().broadcastEntityEvent(this, NOD);
                if (level() instanceof ServerLevel serverLevel)
                    serverLevel.sendParticles(ParticleTypes.EFFECT, getX(), getEyeY(), getZ(), 3 + random.nextInt(5), 0.1, 0.1, 0.1, 0.001);
                this.playSound(SoundEvents.SHIELD_BREAK, 1, 0.5f);
                this.playSound(SoundEvents.CONDUIT_DEACTIVATE, 1, 0.5f);
            }
            if (sitDownCounter == 26) setSitting(true);
            if (sitDownCounter == 21) level().broadcastEntityEvent(this, SIT_DOWN);
            if (sitDownCounter == 0) {
                level().broadcastEntityEvent(this, SIT);
                this.setPersistenceRequired();
            }
        }
    }

    private void handleAttack() {
        if (getTarget()!=null && attackCounter == 0) {
            navigation.moveTo(getTarget(), 1);
        }

        if (getTarget()!=null && attackCounter > 0) {
            navigation.moveTo(getTarget(), 0.5);
            if (attackCounter == 51) {
                level().broadcastEntityEvent(this, ATTACK);
            }
            if (attackCounter == 21) {
                this.playSound(SoundEvents.SHIELD_BREAK, 1, 0.5f);
                AOEManager.nomadKnockback(level(), this);
            }
            attackCounter--;
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0,2.5,-1).yRot((float) Math.toRadians(-this.getYHeadRot())).add(0,Math.sin(tickCount*0.02)*0.5,0);
    }

    @Override
    public int getMaxHeadYRot() {
        return 40;
    }

    @Override
    protected float getMaxHeadRotationRelativeToBody() {
        return isSitting() ? 100 : 40;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public class NomadNavigation extends GroundPathNavigation {
        private static final int STUCK_THRESHOLD = 60;
        private static final int TELEPORT_COOLDOWN = 200;
        private static final double TELEPORT_RANGE = 8.0;
        private static final double MIN_GAP_WIDTH = 2.0;

        private int stuckTimer = 0;
        private int teleportCooldown = 0;
        private Vec3 lastPosition = Vec3.ZERO;

        public NomadNavigation(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        public void tick() {
            super.tick();

            //if (teleportCooldown > 0) {
            //    teleportCooldown--;
            //}
//
            //if (!isDone()) {
            //    Vec3 currentPos = mob.position();
            //    double distanceMoved = currentPos.distanceTo(lastPosition);
//
            //    if (distanceMoved < 0.1 && currentPos.distanceTo(getTargetPos().getCenter()) > 1.0) {
            //        stuckTimer++;
//
            //        if (stuckTimer >= STUCK_THRESHOLD && teleportCooldown <= 0) {
            //            TeleportUtils.directionalTeleport(level, this.mob, getTargetPos(), 10);
            //            stuckTimer = 0;
            //            teleportCooldown = TELEPORT_COOLDOWN;
            //        }
            //    } else {
            //        stuckTimer = 0;
            //    }
//
            //    lastPosition = currentPos;
            //}
        }

        private boolean isFacingGap() {
            if (getTargetPos() == null) return false;

            Vec3 mobPos = mob.position();
            Vec3 targetPos = getTargetPos().getCenter();
            Vec3 direction = targetPos.subtract(mobPos).normalize();

            double checkDistance = Math.min(TELEPORT_RANGE, mobPos.distanceTo(targetPos));

            for (double d = 1.0; d < checkDistance; d += 0.5) {
                Vec3 checkPos = mobPos.add(direction.scale(d));
                BlockPos blockPos = BlockPos.containing(checkPos);

                if (!mob.level().getBlockState(blockPos.below()).isSolid()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void stop() {
            super.stop();
            //stuckTimer = 0;
            //lastPosition = mob.position();
        }
    }

    class NomadBodyRotationControl extends BodyRotationControl {
        public NomadBodyRotationControl(AbyssalNomadEntity nomad) {
            super(nomad);
        }

        public void clientTick() {
            if (!AbyssalNomadEntity.this.isSitting()) {
                super.clientTick();
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!isSitting() && itemstack.is(BeyondTags.REMEMBRANCES)) {
            sitDownCounter = 60;
            if (!player.isCreative()) itemstack.shrink(1);
            player.addEffect(new MobEffectInstance(BeyondEffects.NOMADS_BLESSING, 6000));
            return InteractionResult.SUCCESS;
        }

        if (isSitting()) {
            level().broadcastEntityEvent(this, STAND_UP);
            setSitting(false);
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }

        if (getCorruption() == 0 && !isSitting()) {
            sitDownCounter = 27;
            setSitting(true);
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        setSitting(false);
        level().broadcastEntityEvent(this, STAND_UP);

        if (source.getEntity() != null && source.getEntity() instanceof LivingEntity livingEntity && livingEntity.isAttackable()) {
            if (livingEntity instanceof Player player && (player.isCreative() || player.isSpectator()))
                return super.hurt(source, amount);
            setTarget(livingEntity);
        }

        return super.hurt(source, amount);
    }

    public class attackGoal extends Goal {
        AbyssalNomadEntity nomad;

        public attackGoal(AbyssalNomadEntity nomad) {
            this.nomad = nomad;
        }
        @Override
        public boolean canUse() {
            return nomad.getTarget() != null;
        }

        @Override
        public void start() {
            super.start();
            nomad.setSitting(false);
        }

        @Override
        public void tick() {
            super.tick();
            //if (getCorruption()!=255) {
                nomad.setYHeadRot(getYHeadRot()+(random.nextInt(-50, 50)));
                nomad.setCorruption((int) Mth.lerp(random.nextFloat(), getCorruption() / 3f, 255));
                nomad.playSound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1, random.nextFloat()*2);
            //}

            if (nomad.getTarget().position().subtract(nomad.position()).length() < 5) {
                if (nomad.attackCounter == 0) {
                    nomad.attackCounter = 51;
                }
            }
        }

        @Override
        public void stop() {
            super.stop();
            nomad.setCorruption(255);
        }
    }

    public class PersistentMoveGoal extends Goal {
        private final AbyssalNomadEntity nomad;
        private int stuckTicks = 0;
        private Vec3 lastPosition = Vec3.ZERO;

        public PersistentMoveGoal(AbyssalNomadEntity nomad) {
            this.nomad = nomad;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return nomad.isVehicle() && nomad.getCorruption() == 0;
        }

        @Override
        public void start() {
            super.start();
            if (nomad.level() instanceof ServerLevel serverLevel) nomad.prayerSite = serverLevel.getLevel().findNearestMapStructure(BeyondTags.NOMAD_PRAYER_SITE, this.nomad.getOnPos(), 200, false);
        }

        @Override
        public void tick() {
            if (nomad.prayerSite == null) return;
            BlockPos t = nomad.prayerSite;
            BlockPos target = new BlockPos(t.getX(), nomad.getBlockY(), t.getZ());
            double distance = nomad.position().distanceTo(Vec3.atCenterOf(target));

            if (distance < 3) return;

            Vec3 currentPos = nomad.position();
            if (currentPos.distanceTo(lastPosition) < 0.25) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastPosition = currentPos;

            if (stuckTicks > 20) {
                teleportToward(target);
                stuckTicks = 0;
                return;
            }
//
            if (distance > 32) {
                moveToward(target, 1.5);
            } else if (distance > 10) {
                moveToward(target, 1.0);
            }
        }

        private void moveToward(BlockPos target, double speed) {
            if (!nomad.getNavigation().isInProgress() || nomad.tickCount % 40 == 0) {
                nomad.getNavigation().moveTo(
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        speed
                );
            }

            //nomad.level().setBlock(target, BeyondBlocks.MEMOR_PILLAR.get().defaultBlockState(), 3);
        }

        private void teleportToward(BlockPos target) {
            Vec3 current = nomad.position();
            Vec3 targetVec = Vec3.atCenterOf(target);
            Vec3 direction = targetVec.subtract(current).normalize();

            double teleportDist = 8 + nomad.getRandom().nextDouble() * 8;
            teleportDist = Math.min(teleportDist, current.distanceTo(targetVec) * 0.7);

            Vec3 newPos = current.add(direction.scale(teleportDist));
            BlockPos landing = findSpot(newPos);

            if (landing != null && !nomad.level().isClientSide) {
                nomad.level().broadcastEntityEvent(nomad, NOD);
                //nomad.level().gameEvent(GameEvent.TELEPORT, nomad.position(), GameEvent.Context.of(nomad));
                //nomad.level().playSound((Player)null, nomad.getX(), nomad.getY(), nomad.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT, nomad.getSoundSource());
                //nomad.teleportTo(landing.getX() + 0.5, landing.getY() + 1, landing.getZ() + 0.5);
                nomad.teleport(landing.getX() + 0.5, landing.getY() + 1, landing.getZ() + 0.5);
            }
        }

        private BlockPos findSpot(Vec3 position) {
            BlockPos pos = BlockPos.containing(position);
            Level level = nomad.level();

            if ((level.getBlockState(pos.below()).is(BeyondBlocks.AURORACITE) || level.getBlockState(pos.below()).isSolid()) &&
                    level.getBlockState(pos).isAir() &&
                    level.getBlockState(pos.above()).isAir()) {
                return pos;
            }

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos check = pos.offset(x, 0, z);
                    if ((level.getBlockState(check.below()).is(BeyondBlocks.AURORACITE) || level.getBlockState(pos.below()).isSolid()) &&
                            level.getBlockState(check).isAir() &&
                            level.getBlockState(check.above()).isAir()) {
                      return check;
                    }
                }
            }

            return null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && nomad.prayerSite != null && nomad.position().distanceTo(Vec3.atCenterOf(nomad.prayerSite)) > 8;
        }

        @Override
        public void stop() {
            super.stop();
            if (nomad.isVehicle()) {
                LivingEntity entity = nomad.getControllingPassenger();
                if (entity != null) entity.addEffect(new MobEffectInstance(BeyondEffects.NOMADS_BLESSING, 1200));
                ejectPassengers();
            }

            nomad.sitDownCounter = 27;
            setSitting(true);
        }
    }

    private boolean teleport(double x, double y, double z) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(x, y, z);

        while(blockpos$mutableblockpos.getY() > this.level().getMinBuildHeight() && !this.level().getBlockState(blockpos$mutableblockpos).blocksMotion()) {
            blockpos$mutableblockpos.move(Direction.DOWN);
        }

        BlockState blockstate = this.level().getBlockState(blockpos$mutableblockpos);
        boolean flag = blockstate.blocksMotion();
        boolean flag1 = blockstate.getFluidState().is(FluidTags.WATER);
        if (flag && !flag1) {
            EntityTeleportEvent.EnderEntity event = EventHooks.onEnderTeleport(this, x, y, z);
            if (event.isCanceled()) {
                return false;
            } else {
                Vec3 vec3 = this.position();
                boolean flag2 = this.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true);
                if (flag2) {
                    this.level().gameEvent(GameEvent.TELEPORT, vec3, GameEvent.Context.of(this));
                    if (!this.isSilent()) {
                        this.level().playSound((Player)null, this.xo, this.yo, this.zo, SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
                        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                    }
                }

                return flag2;
            }
        } else {
            return false;
        }
    }
}
