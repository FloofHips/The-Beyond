package com.thebeyond.common.entity;

import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.common.entity.util.SlowRotMoveControl;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.util.AOEManager;
import com.thebeyond.util.ColorUtils;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumSet;
import java.util.List;

public class AbyssalNomadEntity extends PathfinderMob {

    private static final byte SIT = 67;
    private static final byte SIT_DOWN = 68;
    private static final byte NOD = 69;
    private static final byte ATTACK = 70;
    private static final byte STAND_UP = 71;
    private static final byte DROP = 72;

    public final AnimationState sitAnimationState = new AnimationState();
    public final AnimationState sitPoseAnimationState = new AnimationState();
    public final AnimationState standUpAnimationState = new AnimationState();
    public final AnimationState nodAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState dropAnimationState = new AnimationState();
    public BlockPos prayerSite;
    public BlockPos lookAt;
    private int stuckTicks = 0;
    private Vec3 lastPosition = Vec3.ZERO;

    public static final EntityDataAccessor<Boolean> DATA_SITTING = SynchedEntityData.defineId(AbyssalNomadEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> DATA_TO_PRAY = SynchedEntityData.defineId(AbyssalNomadEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> DATA_CORRUPTION = SynchedEntityData.defineId(AbyssalNomadEntity.class, EntityDataSerializers.INT);

    public int sitDownCounter = 0;
    public int attackCounter = 0;
    public int dropCounter = 0;
    public int uncorruptCounter = 0;

    public boolean isSitting() {
        return this.entityData.get(DATA_SITTING);
    }
    public void setSitting(boolean i) {
        this.entityData.set(DATA_SITTING, i);
    }
    public boolean isPraying() {
        return this.entityData.get(DATA_TO_PRAY);
    }
    public void setPray(boolean i) {
        this.entityData.set(DATA_TO_PRAY, i);
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
        this.navigation = new GroundPathNavigation(this, level);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new NomadBodyRotationControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SITTING, false);
        builder.define(DATA_TO_PRAY, false);
        builder.define(DATA_CORRUPTION, level().random.nextInt(100,256));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        setSitting(compound.getBoolean("Sitting"));
        setCorruption(compound.getInt("Corruption"));
        setPray(compound.getBoolean("toPray"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Sitting", entityData.get(DATA_SITTING));
        compound.putInt("Corruption", entityData.get(DATA_CORRUPTION));
        compound.putBoolean("toPray", entityData.get(DATA_TO_PRAY));
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
            if (id == DROP) {
                this.dropAnimationState.start(this.tickCount);
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
        handlePray();
        handleLook();

        if (isPraying() && prayerSite == null && level() instanceof ServerLevel serverLevel)
            prayerSite = serverLevel.getLevel().findNearestMapStructure(BeyondTags.NOMAD_PRAYER_SITE, this.getOnPos(), 200, false);

        if (this.position().y < this.level().getMinBuildHeight() - 5) TeleportUtils.randomTeleport(this.level(), this);
    }

    private void handleLook() {
        if (lookAt!=null) {
            Vec3 target = Vec3.atCenterOf(lookAt);
            Vec3 direction = target.subtract(this.position());

            float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;

            this.setYRot(Mth.rotLerp(0.5F, this.getYRot(), yaw));
            this.setYHeadRot(Mth.rotLerp(0.5F, this.getYHeadRot(), yaw));
        }
    }

    private void handlePray() {
        if (dropCounter > 0) {
            dropCounter--;

            if (dropCounter == 59) level().broadcastEntityEvent(this, DROP);
            if (dropCounter == 58) {
                level().broadcastEntityEvent(this, STAND_UP);
                this.setSitting(false);
            }

            if (dropCounter == 30) {
                ItemEntity itementity = new ItemEntity(this.level(), this.getX(), this.getEyeY(), this.getZ(), new ItemStack(BeyondItems.ABYSSAL_SHROUD.get(), 1+random.nextInt(0, 2)));
                itementity.setNoPickUpDelay();
                this.level().addFreshEntity(itementity);
                itementity.setNoGravity(true);

                if (level() instanceof ServerLevel serverLevel)
                    serverLevel.sendParticles(ColorUtils.auroraOptions, itementity.getX(), itementity.getY(), itementity.getZ(), 3 + random.nextInt(5), 0.05, 0.05, 0.05, 0.001);

                this.playSound(SoundEvents.AXE_STRIP, 1, 0.5f);
            }

            if (dropCounter == 2) lookAt = null;
        }
    }

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
                this.navigation.stop();
                setSitting(true);
                level().broadcastEntityEvent(this, SIT);
                this.setPersistenceRequired();
            }
        }
    }

    private void handleUncorrupt() {
        if (uncorruptCounter > 0 && getCorruption() > 0) {
            uncorruptCounter--;

            if(getCorruption() != 0) {
                this.setYHeadRot(getYHeadRot()+(random.nextInt(-50, 50)));
                setCorruption((int) Mth.lerp(random.nextFloat(), Math.min(getCorruption() * 3, 255), 0));
                this.playSound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1, random.nextFloat()*2);
            }

            if (uncorruptCounter == 20) {
                setCorruption(1);
                level().broadcastEntityEvent(this, NOD);
                if (level() instanceof ServerLevel serverLevel)
                    serverLevel.sendParticles(ParticleTypes.EFFECT, getX(), getEyeY(), getZ(), 3 + random.nextInt(5), 0.1, 0.1, 0.1, 0.001);
                this.playSound(SoundEvents.SHIELD_BREAK, 1, 0.5f);
                this.playSound(SoundEvents.CONDUIT_DEACTIVATE, 1, 0.5f);
            }
            if (uncorruptCounter == 0) {
                setCorruption(0);
            }
        }
    }

    private void handleAttack() {
        if (attackCounter == 51) {
            level().broadcastEntityEvent(this, ATTACK);
        }
        if (attackCounter == 21) {
            this.playSound(SoundEvents.SHIELD_BREAK, 1, 0.5f);
            AOEManager.nomadKnockback(level(), this);
        }

        if(attackCounter > 0) {
            attackCounter--;
            return;
        }

        if (getTarget()!=null) {
            BlockPos t = getTarget().getOnPos();
            BlockPos target = new BlockPos(t.getX(), this.getBlockY(), t.getZ());
            double distance = this.position().distanceTo(Vec3.atCenterOf(target));

            if (distance < 3) return;

            Vec3 currentPos = this.position();
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

            if (distance > 32) {
                moveToward(target, 1.5);
            } else if (distance > 10) {
                moveToward(target, 1.0);
            }
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

            AABB box = new AABB(this.blockPosition()).inflate(10);
            List<AbyssalNomadEntity> nomads = level().getEntitiesOfClass(AbyssalNomadEntity.class, box);

            for (AbyssalNomadEntity nomad : nomads) nomad.setPray(true);

            return InteractionResult.SUCCESS;
        }

        if (getCorruption() == 0 && !isSitting()) {
            sitDownCounter = 27;
            this.navigation.stop();
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

        public PersistentMoveGoal(AbyssalNomadEntity nomad) {
            this.nomad = nomad;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return nomad.isPraying();
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

            //nomad.lookAt = nomad.prayerSite;

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

            if (distance > 32) {
                moveToward(target,1.5);
            } else if (distance > 10) {
                moveToward(target, 1.0);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && nomad.position().distanceTo(Vec3.atCenterOf(nomad.prayerSite)) > 8;
        }

        @Override
        public void stop() {
            super.stop();
            nomad.lookAt = null;
            nomad.setPray(false);
        }
    }

    public boolean teleport(double x, double y, double z) {
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

    public void moveToward(BlockPos target, double speed) {
        if (!this.getNavigation().isInProgress() || this.tickCount % 40 == 0)
            this.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), speed);
    }

    public void teleportToward(BlockPos target) {
        Vec3 current = this.position();
        Vec3 targetVec = Vec3.atCenterOf(target);
        Vec3 direction = targetVec.subtract(current).normalize();

        double teleportDist = 8 + this.getRandom().nextDouble() * 8;
        teleportDist = Math.min(teleportDist, current.distanceTo(targetVec) * 0.7);

        Vec3 newPos = current.add(direction.scale(teleportDist));
        BlockPos landing = findSpot(newPos);

        if (landing != null && !this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, NOD);
            this.teleport(landing.getX() + 0.5, landing.getY() + 1, landing.getZ() + 0.5);
        }
    }

    private BlockPos findSpot(Vec3 position) {
        BlockPos pos = BlockPos.containing(position);
        Level level = this.level();

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
}
