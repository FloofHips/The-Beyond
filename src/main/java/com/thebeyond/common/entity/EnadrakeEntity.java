package com.thebeyond.common.entity;

import com.thebeyond.common.block.EnadrakeHutBlock;
import com.thebeyond.common.block.EnatiousTotemSeedBlock;
import com.thebeyond.common.block.ObirootSproutBlock;
import com.thebeyond.common.block.RefugeBlock;
import com.thebeyond.common.block.blockentities.EnadrakeHutBlockEntity;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.common.block.blockstates.HutHeightProperty;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.*;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import net.neoforged.neoforge.registries.DeferredRegister;

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

     private boolean fleeToHut = false;
     public boolean shouldFleeToHut() { return fleeToHut; }
     public void setFleeToHut(boolean flee) { this.fleeToHut = flee; }

    public EnadrakeEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setCanPickUpLoot(true);
        this.setPersistenceRequired();
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EnadrakeSearchForItemsGoal());
        //this.goalSelector.addGoal(0, new EnadrakeAdvanceStairGoal(this, 1.2, 10, 16));
        this.goalSelector.addGoal(0, new EnadrakeReproduceGoal(this, 1.2, 16, 5));
        this.goalSelector.addGoal(1, new EnadrakeStoreInHomeGoal(this, 1.2, 10, 16));
        this.goalSelector.addGoal(0, new EnadrakeBuildRefugeGoal(this, 1.2, 10, 10));
        this.goalSelector.addGoal(1, new EnadrakeEnterHutGoal(this, 1.2, 15, 10));
        this.goalSelector.addGoal(1, new EnadrakeHarvestGoal(this, 1.5, 15, 6));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
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
        if (panic == 189) {
            Player player = this.level().getNearestPlayer(this, 16);

            if (player != null) {
                this.lookAt(player, 180, 180);
                player.addEffect(new MobEffectInstance(BeyondEffects.DEAFENED, 1200));
            }
        }
        if (panic == 175) setDataScream(false);
        if (this.navigation.isStuck()) level().addParticle(ColorUtils.dustOptions, this.getX(), this.getY(), this.getZ(), 0,0,0);
    }

    public void scream() {
        level().playSound(this, BlockPos.containing(this.position()), SoundEvents.HORSE_DEATH, SoundSource.HOSTILE, 0.5f, 1);
        level().playSound(this, BlockPos.containing(this.position()), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2, 2);

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

         if (level() instanceof ServerLevel) {
             this.setFleeToHut(true);
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

        if (!level().isClientSide) {
            if (!this.getMainHandItem().isEmpty() && !itemstack.isEmpty()) {

                ItemEntity itementity = new ItemEntity(this.level(), this.getX(), this.getEyeY(), this.getZ(), this.getMainHandItem());
                itementity.setDeltaMovement(itementity.getDeltaMovement().add(0,0.2,0));
                this.level().addFreshEntity(itementity);

                this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(itemstack.getItem(), 1));
                itemstack.consume(1, player);

                if (player instanceof ServerPlayer serverPlayer) {
                    BeyondCriteriaTriggers.GIFT_ENADRAKE.get().trigger(serverPlayer);
                }

                return InteractionResult.SUCCESS;
            }

            if (itemstack.is(Items.BONE_MEAL)) {
                itemstack.consume(1, player);
                if(this.level() instanceof ServerLevel level)
                    this.growUp(level);
                return InteractionResult.SUCCESS;
            } else if (!itemstack.isEmpty()) {
                ItemStack playerItem = new ItemStack(itemstack.getItem(), 1);
                itemstack.consume(1, player);
                this.setItemInHand(InteractionHand.MAIN_HAND, playerItem);

                if (player instanceof ServerPlayer serverPlayer) {
                    BeyondCriteriaTriggers.GIFT_ENADRAKE.get().trigger(serverPlayer);
                }

                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME_PARTIAL;
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
            Player player = level().getNearestPlayer(this, 32);

            if (player != null && player instanceof ServerPlayer serverPlayer) {
                BeyondCriteriaTriggers.GIFT_ENADRAKE.get().trigger(serverPlayer);
            }
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
            if (!level().isRaining()) return false;

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
        private int searchTicks = 0;
        private int stuckTicks = 0;
        private int cooldown = 0;

        public EnadrakeSearchForItemsGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            if (!EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;

            List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
            return !list.isEmpty() && EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
        }

        // --- Reda's original canContinueToUse ---
        //public boolean canContinueToUse() {
        //    return EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
        //}

        public boolean canContinueToUse() {
            if (!EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;
            if (searchTicks > 100) return false;
            List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
            return !list.isEmpty();
        }

        public void start() {
            searchTicks = 0;
            stuckTicks = 0;
            List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
            if (!list.isEmpty()) {
                EnadrakeEntity.this.getNavigation().moveTo(list.get(0), 1.2);
            }
        }

        // --- Reda's original tick ---
        //public void tick() {
        //    List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
        //    ItemStack itemstack = EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND);
        //    if (itemstack.isEmpty() && !list.isEmpty()) {
        //        EnadrakeEntity.this.getNavigation().moveTo((Entity)list.get(0), (double)1.2F);
        //    }
        //    //if (getNavigation().isStuck()) addDeltaMovement(new Vec3(0, 0.2, 0));
        //}

        public void tick() {
            searchTicks++;

            if (getNavigation().isStuck()) {
                stuckTicks++;
                if (stuckTicks > 60) {
                    searchTicks = 101;
                    cooldown = 100; // ~5 seconds before retrying
                } else if (stuckTicks % 10 == 0) {
                    List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
                    if (!list.isEmpty()) {
                        EnadrakeEntity.this.getNavigation().moveTo(list.get(0), 1.2);
                    }
                }
            } else {
                stuckTicks = 0;
            }

            if (searchTicks % 20 == 0) {
                List<ItemEntity> list = EnadrakeEntity.this.level().getEntitiesOfClass(ItemEntity.class, EnadrakeEntity.this.getBoundingBox().inflate((double)8.0F, (double)8.0F, (double)8.0F), EnadrakeEntity.ALLOWED_ITEMS);
                ItemStack itemstack = EnadrakeEntity.this.getItemBySlot(EquipmentSlot.MAINHAND);
                if (itemstack.isEmpty() && !list.isEmpty()) {
                    EnadrakeEntity.this.getNavigation().moveTo(list.get(0), 1.2);
                }
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

            //if (mob.getNavigation().isStuck()) mob.addDeltaMovement(new Vec3(0, 0.2, 0));

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
            if (itemstack.isEmpty()) return false;
            if (this.getMoveToTarget() == null) return false;

            // Recheck if target hut still has empty slot
            BlockPos targetPos = this.getMoveToTarget().below();
            BlockEntity hut = entity.level().getBlockEntity(targetPos);
            if (hut instanceof EnadrakeHutBlockEntity hutblockentity) {
                if (!hutblockentity.getTheItem().isEmpty()) return false;
            }

            return super.canContinueToUse();
        }

        public double acceptedDistance() {
            return 3.0F;
        }

        @Override
        protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
            BlockState blockstate = levelReader.getBlockState(blockPos);
            if (!blockstate.is(BeyondBlocks.ENADRAKE_HUT.get())) return false;

            // Only target ground-level huts (not elevated on expansion blocks)
            //Block belowBlock = levelReader.getBlockState(blockPos.below()).getBlock();
            //if (belowBlock instanceof EnadrakeHutBlock
            //    || belowBlock == BeyondBlocks.ENADRAKE_FLARE.get()
            //    || belowBlock == BeyondBlocks.ENGRAVED_END_STONE.get()) return false;

            BlockEntity hut = entity.level().getBlockEntity(blockPos);
            if (!(hut instanceof EnadrakeHutBlockEntity hutblockentity)) return false;
            return hutblockentity.getTheItem().isEmpty();
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

            //if (mob.getNavigation().isStuck()) mob.addDeltaMovement(new Vec3(0, 0.2, 0));

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

    class EnadrakeBuildRefugeGoal extends MoveToBlockGoal {
        EnadrakeEntity entity;
        boolean reachedTarget;

        public EnadrakeBuildRefugeGoal(PathfinderMob mob, double speedModifier, int searchRange, int verticalSearchRange) {
            super(mob, speedModifier, searchRange, verticalSearchRange);
            this.entity = (EnadrakeEntity) mob;
        }

        @Override
        public boolean canUse() {
            ItemStack itemstack = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemstack.isEmpty() && itemstack.getRarity() != Rarity.EPIC) return super.canUse();
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
            if (blockstate.is(BeyondBlocks.REFUGE.get())) {
                if (!blockstate.getValue(RefugeBlock.POWERED))
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
                BlockEntity refuge = level().getBlockEntity(pos);
                if (refuge instanceof RefugeBlockEntity refugeBlockEntity) {
                    refugeBlockEntity.printPattern();
                    entity.getItemBySlot(EquipmentSlot.MAINHAND).shrink(1);
                    entity.panic = 50;
                }
            }

            //if (mob.getNavigation().isStuck()) mob.addDeltaMovement(new Vec3(0, 0.2, 0));

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

    class EnadrakeEnterHutGoal extends MoveToBlockGoal {
        EnadrakeEntity entity;
        boolean reachedTarget;
        int stuckTicks = 0;
        boolean giveUp;

        public EnadrakeEnterHutGoal(PathfinderMob mob, double speedModifier, int searchRange, int verticalSearchRange) {
            super(mob, speedModifier, searchRange, verticalSearchRange);
            this.entity = (EnadrakeEntity) mob;
        }

        @Override
        protected int nextStartTick(PathfinderMob mob) {
            return reducedTickDelay(20 + mob.getRandom().nextInt(40));
        }

        @Override
        public boolean canUse() {
            if (entity.shouldFleeToHut()) return super.canUse();
            ItemStack itemstack = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemstack.isEmpty()) return false;
            if (!level().isRaining()) return super.canUse();
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (giveUp) return false;
            if (level().isRaining()) return false;
            if (this.getMoveToTarget() == null) return false;

            BlockPos targetPos = this.getMoveToTarget().below();
            BlockEntity hut = entity.level().getBlockEntity(targetPos);
            if (hut instanceof EnadrakeHutBlockEntity hutEntity) {
                if (!hutEntity.isAvailable()) return false;
            } else {
                return false;
            }

            return super.canContinueToUse();
        }

        @Override
        public void start() {
            super.start();
            this.stuckTicks = 0;
            this.giveUp = false;

            //BlockPos targetPos = this.getMoveToTarget().below();
            //BlockEntity hut = entity.level().getBlockEntity(targetPos);
            //if (hut instanceof EnadrakeHutBlockEntity hutEntity) {
            //    hutEntity.reserve(entity.getUUID());
            //}
        }

        @Override
        public void stop() {
            this.stuckTicks = 0;
            //if (this.getMoveToTarget() != null) {
            //    BlockPos targetPos = this.getMoveToTarget().below();
            //    BlockEntity hut = entity.level().getBlockEntity(targetPos);
            //    if (hut instanceof EnadrakeHutBlockEntity hutEntity) {
            //        hutEntity.clearReservation(entity.getUUID());
            //    }
            //}

            // --- POD BEHAVIOR: uncomment to reset fleeToHut when enadrake enters hut ---
            // entity.setFleeToHut(false);
            // ---

            super.stop();
        }

        public double acceptedDistance() {
            return 4.0F;
        }

        @Override
        protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
            BlockState blockstate = levelReader.getBlockState(blockPos);
            if (!blockstate.is(BeyondBlocks.ENADRAKE_HUT.get())) return false;

            // Only target ground-level huts (not elevated on expansion blocks)
            //Block belowBlock = levelReader.getBlockState(blockPos.below()).getBlock();
            //if (belowBlock instanceof EnadrakeHutBlock
            //    || belowBlock == BeyondBlocks.ENADRAKE_FLARE.get()
            //    || belowBlock == BeyondBlocks.ENGRAVED_END_STONE.get()) return false;

            BlockEntity hut = entity.level().getBlockEntity(blockPos);
            if (!(hut instanceof EnadrakeHutBlockEntity hutblockentity)) return false;

            // Not occupied AND not reserved by another enadrake
            return hutblockentity.isAvailable();

            // Reserve immediately so other enadrakes pick a different hut
            //hutblockentity.reserve(entity.getUUID());
            //return true;
        }


        public boolean shouldRecalculatePath() {
            return this.tryTicks % 40 == 0;
        }

        protected boolean isReachedTarget() {
            return this.reachedTarget;
        }

        @Override
        public void tick() {
            BlockPos targetPos = this.getMoveToTarget();
            Vec3 enadrakePos = new Vec3(this.mob.position().x, targetPos.getY(), this.mob.position().z);

            if (!targetPos.closerToCenterThan(enadrakePos, this.acceptedDistance())) {
                this.reachedTarget = false;
                ++this.tryTicks;
                if (this.shouldRecalculatePath()) {
                    this.mob.getNavigation().moveTo((double)targetPos.getX() + (double)0.5F, (double)targetPos.getY(), (double)targetPos.getZ() + (double)0.5F, this.speedModifier);
                }
            } else {
                this.reachedTarget = true;
                --this.tryTicks;
            }

            if (isReachedTarget()) {
                BlockPos pos = this.getMoveToTarget().below();
                BlockEntity hut = level().getBlockEntity(pos);
                if (hut instanceof EnadrakeHutBlockEntity enadrakeHutBlockEntity) {
                    if (!enadrakeHutBlockEntity.tryToEnter(this.entity)) {
                        giveUp = true;
                    }
                } else {
                    giveUp = true;
                }
            }

            // Give up if stuck for too long (e.g. blocked by flares/walls)
            if (mob.getNavigation().isStuck()) {
                stuckTicks++;
                if (stuckTicks > 60) { // ~3 seconds stuck → find another hut
                    giveUp = true;
                } else if (stuckTicks % 10 == 0) {
                    BlockPos blockpos = this.getMoveToTarget();
                    this.mob.getNavigation().moveTo(
                            (double)blockpos.getX() + 0.5, (double)blockpos.getY(), (double)blockpos.getZ() + 0.5,
                            this.speedModifier);
                }
            } else {
                stuckTicks = 0;
            }
        }
    }

    class EnadrakeReproduceGoal extends MoveToBlockGoal {
        EnadrakeEntity entity;
        boolean reachedTarget;
        int stuckTicks = 0;
        boolean giveUp;

        public EnadrakeReproduceGoal(PathfinderMob mob, double speedModifier, int searchRange, int verticalSearchRange) {
            super(mob, speedModifier, searchRange, verticalSearchRange);
            this.entity = (EnadrakeEntity) mob;
        }

        @Override
        public boolean canUse() {
            ItemStack itemstack = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (itemstack.is(BeyondBlocks.OBIROOT_SPROUT.asItem())) return super.canUse();
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (giveUp) return false;
            if (this.getMoveToTarget() == null) return false;
            if (!isValidTarget(level(), this.getMoveToTarget().below())) return false;
            return super.canContinueToUse();
        }

        @Override
        public void start() {
            super.start();
            //this.stuckTicks = 0;
        }

        @Override
        public void stop() {
            //this.stuckTicks = 0;
            super.stop();
        }

        public double acceptedDistance() {
            return 3.0F;
        }

        @Override
        protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
            if (!levelReader.getBlockState(blockPos).is(BeyondTags.END_FLOOR_BLOCKS)) return false;
            return levelReader.getBlockState(blockPos.above()).isAir();
        }

        @Override
        public void tick() {
            super.tick();

            if (this.isReachedTarget()) {
                BlockPos pos = this.getMoveToTarget(); // blockPos.above() = air above floor
                if (level().getBlockState(pos).isAir()) {
                    this.mob.getMainHandItem().consume(1, this.entity);
                    level().setBlock(pos, BeyondBlocks.OBIROOT_SPROUT.get().defaultBlockState().setValue(ObirootSproutBlock.AGE, 1), 3);
                }
                this.stop();
            }

            // Give up if stuck for too long (e.g. blocked by flares/walls)
            if (mob.getNavigation().isStuck()) {
                stuckTicks++;
                if (stuckTicks > 60) {
                    giveUp = true;
                }
            } else {
                stuckTicks = 0;
            }
        }
    }
}
