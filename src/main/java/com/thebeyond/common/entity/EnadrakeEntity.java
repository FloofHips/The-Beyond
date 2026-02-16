package com.thebeyond.common.entity;

import com.thebeyond.common.block.EnadrakeHutBlock;
import com.thebeyond.common.block.EnatiousTotemSeedBlock;
import com.thebeyond.common.block.ObirootSproutBlock;
import com.thebeyond.common.block.blockentities.EnadrakeHutBlockEntity;
import com.thebeyond.common.block.blockstates.HutHeightProperty;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

public class EnadrakeEntity extends PathfinderMob {

    static final Predicate<ItemEntity> ALLOWED_ITEMS = (p_350086_) -> !p_350086_.hasPickUpDelay() && p_350086_.isAlive();
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(EnadrakeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_SCREAM = SynchedEntityData.defineId(EnadrakeEntity.class, EntityDataSerializers.BOOLEAN);
    public int panic;
    private boolean insideHut = false;
    private BlockPos hutPosition = null;
    private int inHutTime = 0;

    public EnadrakeEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setCanPickUpLoot(true);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EnadrakeSearchForItemsGoal());
        this.goalSelector.addGoal(0, new EnadrakeAdvanceStairGoal(this, 1.2, 10, 16));
        this.goalSelector.addGoal(1, new EnadrakeHarvestGoal(this, 1.5, 15, 6));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(0, new EnadrakeHurtGoal(this, 1.5));
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 3f));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, EnderglopEntity.class, true));
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.20000000298023224);
    }
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
        builder.define(DATA_SCREAM, false);
    }

    public void setDataScream(boolean i) {
        entityData.set(DATA_SCREAM, i);
    }
    public boolean getDataScream() {
        return entityData.get(DATA_SCREAM);
    }
    public boolean isInsideHut() { return insideHut; }
    public void setInsideHut(boolean inside) { this.insideHut = inside; }
    public BlockPos getHutPosition() { return hutPosition; }
    public void setHutPosition(BlockPos pos) { this.hutPosition = pos; }
    public int getInHutTime() { return inHutTime; }
    public void setInHutTime(int time) { this.inHutTime = time; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("InsideHut", insideHut);
        if (hutPosition != null) {
            tag.putLong("HutPosition", hutPosition.asLong());
        }
        tag.putInt("InHutTime", inHutTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        insideHut = tag.getBoolean("InsideHut");
        if (tag.contains("HutPosition")) {
            hutPosition = BlockPos.of(tag.getLong("HutPosition"));
        }
        inHutTime = tag.getInt("InHutTime");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity) panic=220;
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (panic > 0) panic--;
        if (panic > 190) this.setYHeadRot(getYHeadRot()+(random.nextInt(-40, 40)));
        if (panic < 190 && panic > 140) this.setYHeadRot(getYHeadRot()+(random.nextInt(-10, 10)));
        if (panic == 190) scream();
        if (panic == 175) setDataScream(false);
    }

    public void scream() {
        level().playSound(this, BlockPos.containing(this.position()), SoundEvents.HORSE_DEATH, SoundSource.HOSTILE, 0.5f, 1);
        level().playSound(this, BlockPos.containing(this.position()), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2, 2);

        Player player = this.level().getNearestPlayer(this, 16);

        AABB detectionBox = new AABB(getOnPos()).inflate(10);
        TargetingConditions conditions = TargetingConditions.forNonCombat().range(6).ignoreLineOfSight().selector((entity) -> entity instanceof EnadrakeEntity friend && friend.panic == 0);
        EnadrakeEntity friend = (EnadrakeEntity) level().getNearestEntity(EnadrakeEntity.class, conditions, null, position().x, position().y, position().z, detectionBox);

        setDataScream(true);
        BlockPos pos = findNearestSeed();

        if (level() instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, position().x, position().y+0.6, position().z, random.nextInt(3,6), 0.1, 1, 0.1, 0.01);

        if (pos != null) {
            EnatiousTotemSeedBlock seed = (EnatiousTotemSeedBlock) level().getBlockState(pos).getBlock();
            seed.activate(level().getBlockState(pos), level(), pos);
        }

        if (friend != null) {
            friend.panic = 192;
        }

        if (player != null) {
            this.lookAt(player, 180, 180);
            //player.addEffect(new MobEffectInstance(BeyondEffects.DEAFENED, 250));
        }
    }

    protected BlockPos findNearestSeed() {
        int i = 16;
        int j = 16;
        BlockPos blockpos = this.blockPosition();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for(int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
            for(int l = 0; l < i; ++l) {
                for(int i1 = 0; i1 <= l; i1 = i1 > 0 ? -i1 : 1 - i1) {
                    for(int j1 = i1 < l && i1 > -l ? l : 0; j1 <= l; j1 = j1 > 0 ? -j1 : 1 - j1) {
                        blockpos$mutableblockpos.setWithOffset(blockpos, i1, k - 1, j1);
                        if (this.isWithinRestriction(blockpos$mutableblockpos) && this.isValidTarget(this.level(), blockpos$mutableblockpos)) {
                            return blockpos$mutableblockpos;
                        }
                    }
                }
            }
        }

        return null;
    }

    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        if (!level.getBlockState(pos).is(BeyondBlocks.ENATIOUS_TOTEM_SEED.get())) return false;
        return !level.getBlockState(pos).getValue(EnatiousTotemSeedBlock.POWERED);
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
            this.onItemPickup(itemEntity);
            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack.split(1));
            this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            this.take(itemEntity, 1);
            //itemEntity.discard();
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

            if (getNavigation().isStuck()) addDeltaMovement(new Vec3(0, 0.2, 0));
        }

        public void start() {
            List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
            if (!list.isEmpty()) {
                EnadrakeEntity.this.getNavigation().moveTo((Entity)list.get(0), (double)1.2F);
            }
        }
    }

    class EnadrakeGoHomeGoal extends Goal {

        @Override
        public boolean canUse() {
            return false;
        }
    }

    class EnadrakeHurtGoal extends PanicGoal {
        EnadrakeEntity mob;
        public EnadrakeHurtGoal(PathfinderMob mob, double speedModifier) {
            super(mob, speedModifier);
            this.mob = (EnadrakeEntity) mob;
        }

        @Override
        protected boolean shouldPanic() {
            return super.shouldPanic() || (mob != null && mob.panic > 0 && mob.panic < 180);
        }

        @Override
        public void start() {
            super.start();
        }
    }

    class EnadrakePlantFriendGoal extends Goal {

        @Override
        public boolean canUse() {
            return false;
        }
    }

    class EnadrakeAdvanceStairGoal extends MoveToBlockGoal {
        EnadrakeEntity entity;
        boolean reachedTarget;
        public EnadrakeAdvanceStairGoal(PathfinderMob mob, double speedModifier, int searchRange, int verticalSearchRange) {
            super(mob, speedModifier, searchRange, verticalSearchRange);
            this.entity = (EnadrakeEntity) mob;
        }

        @Override
        public boolean canUse() {
            ItemStack itemstack = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemstack.isEmpty()) return super.canUse();
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            ItemStack itemstack = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.canContinueToUse() && !itemstack.isEmpty();
        }

        public double acceptedDistance() {
            return 3.0F;
        }

        @Override
        protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
            BlockState blockstate = levelReader.getBlockState(blockPos);
            if (blockstate.is(Blocks.END_STONE_BRICK_STAIRS)) {
                Direction d = blockstate.getValue(StairBlock.FACING);
                BlockState state = levelReader.getBlockState(blockPos.offset(d.getStepX(), 0, d.getStepZ()));
                if (state.is((Blocks.END_STONE_BRICK_STAIRS))) return false;
                state = levelReader.getBlockState(blockPos.offset(d.getStepX(), 1, d.getStepZ()));
                if (state.is((Blocks.END_STONE_BRICK_STAIRS))) return false;
                return true;
            }
            return false;
        }

        protected boolean isReachedTarget() {
            return this.reachedTarget;
        }

        @Override
        public void tick() {
            if (isReachedTarget()) {
                BlockPos pos = this.getMoveToTarget().below();
                BlockState b = mob.level().getBlockState(pos);
                if (b.is(Blocks.END_STONE_BRICK_STAIRS)) {
                    Direction d = b.getValue(StairBlock.FACING);
                    BlockState newState = Blocks.END_STONE_BRICK_STAIRS.defaultBlockState();

                    if (b.getValue(StairBlock.HALF) == Half.TOP) {
                        if (level().random.nextFloat() > 0.5) {
                            Direction c = d.getClockWise();
                            //if (level().random.nextFloat() > 0.5) {
                                c = d.getClockWise();
                                newState = newState.setValue(StairBlock.FACING, c);
                            //} else {
                            //    c = d.getCounterClockWise();
                            //    newState = newState.setValue(StairBlock.FACING, c);
                            //}
                            mob.getMainHandItem().shrink(1);
                            mob.level().setBlock(pos.offset(d.getStepX(), 0, d.getStepZ()).offset(c.getStepX(), 1, c.getStepZ()), newState, Block.UPDATE_ALL);
                            return;
                        } else {
                            mob.getMainHandItem().shrink(1);
                            newState = newState.setValue(StairBlock.FACING, b.getValue(StairBlock.FACING));
                            mob.level().setBlock(pos.offset(d.getStepX(), 1, d.getStepZ()), newState, Block.UPDATE_ALL);
                            return;
                        }
                    }

                    if (b.getValue(StairBlock.HALF) == Half.BOTTOM) {
                        newState = newState.setValue(StairBlock.FACING, b.getValue(StairBlock.FACING));
                        if (level().random.nextBoolean()) {
                            mob.getMainHandItem().shrink(1);
                            newState = newState.setValue(StairBlock.HALF, Half.BOTTOM);
                            mob.level().setBlock(pos.offset(d.getStepX(), 1, d.getStepZ()), newState, Block.UPDATE_ALL);
                            return;
                        }
                        else {
                            mob.getMainHandItem().shrink(1);
                            newState = newState.setValue(StairBlock.HALF, Half.TOP);
                            mob.level().setBlock(pos.offset(d.getStepX(), 0, d.getStepZ()), newState, Block.UPDATE_ALL);
                            return;
                        }
                    }
                }
            }

            if (mob.getNavigation().isStuck()) mob.addDeltaMovement(new Vec3(0, 0.2, 0));

            BlockPos blockpos = new BlockPos(this.getMoveToTarget().getX(), this.getMoveToTarget().getY(), this.getMoveToTarget().getZ());

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

    class EnadrakeStoreInHomeGoal extends MoveToBlockGoal {
        EnadrakeEntity entity;
        boolean reachedTarget;
        public EnadrakeStoreInHomeGoal(PathfinderMob mob, double speedModifier, int searchRange, int verticalSearchRange) {
            super(mob, speedModifier, searchRange, verticalSearchRange);
            this.entity = (EnadrakeEntity) mob;
        }

        @Override
        public boolean canUse() {
            ItemStack itemstack = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemstack.isEmpty()) return super.canUse();
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            ItemStack itemstack = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.canContinueToUse() && !itemstack.isEmpty();
        }

        public double acceptedDistance() {
            return 3.0F;
        }

        @Override
        protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
            BlockState blockstate = levelReader.getBlockState(blockPos);
            if (blockstate.is(BeyondBlocks.ENADRAKE_HUT.get())) {
                BlockEntity hut = entity.level().getBlockEntity(blockPos);
                if (hut instanceof EnadrakeHutBlockEntity hutblockentity) {
                    return hutblockentity.getTheItem().isEmpty();
                }
            }
            return false;
        }

        protected boolean isReachedTarget() {
            return this.reachedTarget;
        }

        @Override
        public void tick() {
            if (isReachedTarget()) {
                BlockPos pos = this.getMoveToTarget().below();
                BlockEntity hut = level().getBlockEntity(pos);
                if (hut instanceof EnadrakeHutBlockEntity hutblockentity) {
                    ItemStack itemstack1 = hutblockentity.getTheItem();
                    if (itemstack1.isEmpty()) {
                        EnadrakeHutBlock.fillHut(entity.getMainHandItem(), level(), pos, entity, hutblockentity, itemstack1);
                        entity.panic = 50;
                    } else {
                        this.stop();
                    }
                }
            }

            if (mob.getNavigation().isStuck()) mob.addDeltaMovement(new Vec3(0, 0.2, 0));

            BlockPos blockpos = new BlockPos(this.getMoveToTarget().getX(), this.getMoveToTarget().getY(), this.getMoveToTarget().getZ());

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

    class EnadrakeBuildRefugeGoal extends Goal {

        @Override
        public boolean canUse() {
            return false;
        }
    }
}
