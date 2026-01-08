package com.thebeyond.common.entity;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.ObirootSproutBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class EnadrakeEntity extends PathfinderMob {

    static final Predicate<ItemEntity> ALLOWED_ITEMS = (p_350086_) -> !p_350086_.hasPickUpDelay() && p_350086_.isAlive();
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(EnadrakeEntity.class, EntityDataSerializers.BYTE);

    public EnadrakeEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setCanPickUpLoot(true);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EnadrakeSearchForItemsGoal());
        this.goalSelector.addGoal(1, new EnadrakeHarvestGoal(this, 1.5, 15, 6));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 3f));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, EnderglopEntity.class, true));
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.20000000298023224);
    }
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
    }
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if(source.getEntity() instanceof LivingEntity livingEntity){
            if(livingEntity.level().isClientSide){
                livingEntity.level().playLocalSound(livingEntity, SoundEvents.HORSE_DEATH, SoundSource.HOSTILE, 0.5f, 1);
                livingEntity.level().playLocalSound(livingEntity, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2, 2);
            }
            this.lookAt(livingEntity, 180, 180);
            livingEntity.addEffect(new MobEffectInstance(BeyondEffects.DEAFENED, 200));
        }
        return super.hurt(source, amount);
    }


    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.BONE_MEAL)) {
            itemstack.consume(1, player);
            if(this.level() instanceof ServerLevel level)
                this.growUp(level);
            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    public boolean isHoldingItem() {
        return this.getFlag(1);
    }

    public void setHoldingItem(boolean holdingItem) {
        this.setFlag(1, holdingItem);
    }

    private void setFlag(int flagId, boolean value) {
        if (value) {
            this.entityData.set(DATA_FLAGS_ID, (byte)((Byte)this.entityData.get(DATA_FLAGS_ID) | flagId));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)((Byte)this.entityData.get(DATA_FLAGS_ID) & ~flagId));
        }

    }

    private boolean getFlag(int flagId) {
        return ((Byte)this.entityData.get(DATA_FLAGS_ID) & flagId) != 0;
    }

    public boolean canHoldItem(ItemStack stack) {
        return this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack itemstack = itemEntity.getItem();
        if (this.canHoldItem(itemstack)) {
            int i = itemstack.getCount();

            this.onItemPickup(itemEntity);
            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack.split(1));
            this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            this.take(itemEntity, itemstack.getCount());
            itemEntity.discard();
            this.setHoldingItem(true);
        }

    }

    private void growUp(ServerLevel level) {
        if (this.random.nextInt(3) == 0) {
            this.navigation.stop();
            level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap((p_258973_) -> {
                return p_258973_.getHolder(BeyondFeatures.OBIROOT.getId());
            }).ifPresent((p_255669_) -> {
                if(((ConfiguredFeature)p_255669_.value()).place(level, level.getChunkSource().getGenerator(), random, BlockPos.containing(this.position())))
                    this.discard();
            });
        }
    }


    class EnadrakeHarvestGoal extends MoveToBlockGoal {
        EnadrakeEntity enadrake;
        Boolean reachedTarget = false;
        public EnadrakeHarvestGoal(EnadrakeEntity mob, double speedModifier, int searchRange, int verticalSearchRange) {
            super(mob, speedModifier, searchRange, verticalSearchRange);
            enadrake = mob;
        }
        @Override
        public boolean canUse() {
            ItemStack itemstack = enadrake.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemstack.isEmpty()) return false;

            return level().isRaining() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            ItemStack itemstack = enadrake.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemstack.isEmpty()) return false;

            return super.canContinueToUse();
        }

        public double acceptedDistance() {
            return 2.0F;
        }

        @Override
        protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
            BlockState blockstate = levelReader.getBlockState(blockPos);
            return blockstate.is(BeyondBlocks.OBIROOT_SPROUT.get()) && blockstate.getValue(ObirootSproutBlock.AGE) == 0;
        }

        protected boolean isReachedTarget() {
            return this.reachedTarget;
        }

        @Override
        public void tick() {
            if (isReachedTarget()) {
                BlockPos blockpos = this.getMoveToTarget().below();
                BlockState blockstate = level().getBlockState(blockpos);


                if (level() instanceof ServerLevel serverLevel) {
                    Vec3 vec3 = Vec3.atCenterOf(blockpos).add((double)0.0F, (double)1, (double)0.0F);
                    serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.PEEPING_OBIROOT.get().defaultBlockState()), vec3.x, vec3.y, vec3.z, 10, 0.0F, 0.0F, 0.0F, 0.0F);
                }
                level().playSound(enadrake, blockpos, SoundEvents.ITEM_BREAK, enadrake.getSoundSource(), 0.5F, 1F);

                FallingBlockEntity fallingblockentity = FallingBlockEntity.fall(level(), blockpos, blockstate);
                fallingblockentity.disableDrop();
            }

            BlockPos blockpos = new BlockPos(this.getMoveToTarget().getX(), (int) this.mob.position().y, this.getMoveToTarget().getZ());

            if (!blockpos.closerToCenterThan(this.mob.position(), this.acceptedDistance())) {
                this.reachedTarget = false;
                ++this.tryTicks;
                if (this.shouldRecalculatePath()) {
                    this.mob.getNavigation().moveTo((double)blockpos.getX() + (double)0.5F, (double)blockpos.getY(), (double)blockpos.getZ() + (double)0.5F, this.speedModifier);
                }
            } else {
                this.reachedTarget = true;
                --this.tryTicks;
            }
        }
    }

    class EnadrakeSearchForItemsGoal extends Goal {
        public EnadrakeSearchForItemsGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        public boolean canUse() {
            if (!EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;

            List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
            return !list.isEmpty() && EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
        }

        public void tick() {
            List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
            ItemStack itemstack = EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (itemstack.isEmpty() && !list.isEmpty()) {
                EnadrakeEntity.this.getNavigation().moveTo((Entity)list.get(0), (double)1.2F);
            }

        }

        public void start() {
            List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
            if (!list.isEmpty()) {
                EnadrakeEntity.this.getNavigation().moveTo((Entity)list.get(0), (double)1.2F);
            }
        }
    }
}