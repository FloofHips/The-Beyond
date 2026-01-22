package com.thebeyond.common.entity;

import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.common.entity.util.SlowRotMoveControl;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.util.TeleportUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.FollowTemptation;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

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

    public static final EntityDataAccessor<Boolean> DATA_SITTING = SynchedEntityData.defineId(AbyssalNomadEntity.class, EntityDataSerializers.BOOLEAN);

    public int sittingDownProgress = 0;
    public int gettingUpProgress = 0;
    public int nodProgress = 0;
    public int attackProgress = 0;

    public boolean isSitting() {
        return this.entityData.get(DATA_SITTING);
    }
    public void setSitting(boolean i) {
        this.entityData.set(DATA_SITTING, i);
    }

    public AbyssalNomadEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new SlowRotMoveControl(this, 20f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SITTING, false);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (this.level().isClientSide) {
            if (id == NOD) {
                this.nodAnimationState.start(this.tickCount);
                return;
            }
            if (id == ATTACK) {
                this.attackAnimationState.start(this.tickCount);
                return;
            }
            if (id == SIT_DOWN) {
                this.sitAnimationState.start(this.tickCount);
                return;
            }
            if (id == STAND_UP) {
                this.standUpAnimationState.start(this.tickCount);
                return;
            }
            if (id == SIT) {
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
        this.goalSelector.addGoal(0, new RandomStrollGoal(this, 1));
        this.goalSelector.addGoal(1, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 3f));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, EnderglopEntity.class, true));
    }

    public static boolean checkMonsterSpawnRules(EntityType<AbyssalNomadEntity> entityType, ServerLevelAccessor serverLevelAccessor, MobSpawnType mobSpawnType, BlockPos blockPos, RandomSource randomSource) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) handleAnimations();
        if (this.position().y < this.level().getMinBuildHeight() - 5) TeleportUtils.randomTeleport(this.level(), this);

        navigation.moveTo(this.getX(), 197f, this.getZ()-10, 0.7);
    }

    private void handleAnimations() {

    }

    @Override
    public int getMaxHeadYRot() {
        return 40;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        this.level().broadcastEntityEvent(this, NOD);
        return super.mobInteract(player, hand);
    }
}
