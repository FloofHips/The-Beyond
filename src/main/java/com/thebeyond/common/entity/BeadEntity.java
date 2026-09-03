package com.thebeyond.common.entity;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockOrientation;
import com.thebeyond.common.entity.util.livingblock.TrinketGrowth;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class BeadEntity extends LivingBlock implements Bucketable {
    private static final EntityDataAccessor<Integer> DATA_DYE_COLOR = SynchedEntityData.defineId(BeadEntity .class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BODY_COLOR = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WAXED = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.STRING);
    //private static final EntityDataAccessor<Boolean> DATA_FROZEN_SIZE = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Byte> DATA_WIDTH = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_HEIGHT = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_DEPTH = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BYTE);

    private static final int[][] SILHOUETTES = {
            {4, 4, 4},
            {4, 12, 4},
            {8, 12, 4},
            {8, 8, 8}
    };

    private static final String[] VARIANTS = {
            "swirl", "losange", "perforated", "pyramid", "eyes"
    };

    public DyeColor getDyeColor() {
        return DyeColor.byId((Integer)this.entityData.get(DATA_DYE_COLOR));
    }
    private void setDyeColor(DyeColor dye) {
        this.entityData.set(DATA_DYE_COLOR, dye.getId());
    }
    public Color getBodyColor() {return new Color(this.entityData.get(DATA_BODY_COLOR));}
    private void setBodyColor(int color) {this.entityData.set(DATA_BODY_COLOR, color);}
    public Boolean isWaxed() {return this.entityData.get(DATA_WAXED);}
    private void setWaxed(boolean waxed) {this.entityData.set(DATA_WAXED, waxed);}
    public String getVariant() {return this.entityData.get(DATA_VARIANT);}
    private void setVariant(String variant) {this.entityData.set(DATA_VARIANT, variant);}
    public List<TrinketGrowth.Feature> getFeaturePlan() {return featurePlan;}
    public void setFeaturePlan(List<TrinketGrowth.Feature> plan) {featurePlan = plan;}

    public int getHeight() {return this.entityData.get(DATA_HEIGHT);}
    private void setHeight(byte height) {this.entityData.set(DATA_HEIGHT, height);}
    public int getWidth() {return this.entityData.get(DATA_WIDTH);}
    private void setWidth(byte width) {this.entityData.set(DATA_WIDTH, width);}
    public int getDepth() {return this.entityData.get(DATA_DEPTH);}
    private void setDepth(byte depth) {this.entityData.set(DATA_DEPTH, depth);}

    public BeadEntity(final EntityType<? extends Mob> type, final Level level) {
        super(type, level);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        setVariant(Arrays.stream(VARIANTS).toList().get(level.getRandom().nextInt(VARIANTS.length)));
        setBodyColor(-1);
        setDyeColor(DyeColor.WHITE);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_DYE_COLOR, DyeColor.WHITE.getId());
        entityData.define(DATA_BODY_COLOR, Color.WHITE.getRGB());
        entityData.define(DATA_WAXED, false);
        entityData.define(DATA_VARIANT, "swirl");
        //entityData.define(DATA_FROZEN_SIZE, false);

        entityData.define(DATA_WIDTH, (byte)1);
        entityData.define(DATA_HEIGHT, (byte)1);
        entityData.define(DATA_DEPTH, (byte)1);
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("DyeColor", (byte)this.getDyeColor().getId());
        compound.putInt("BodyColor", this.getBodyColor().getRGB());
        compound.putBoolean("IsWaxed", this.isWaxed());
        compound.putString("Variant", this.getVariant());
        //compound.putBoolean("FrozenSize", this.entityData.get(DATA_FROZEN_SIZE));

        compound.putByte("Width", (byte) this.getWidth());
        compound.putByte("Height", (byte) this.getHeight());
        compound.putByte("Depth", (byte) this.getDepth());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setDyeColor(DyeColor.byId(compound.getInt("DyeColor")));
        this.setBodyColor(compound.getInt("BodyColor"));
        this.setWaxed(compound.getBoolean("IsWaxed"));
        this.setVariant(compound.getString("Variant"));
        if (compound.contains("Height")) this.setHeight(compound.getByte("Height"));
        if (compound.contains("Width")) this.setWidth(compound.getByte("Width"));
        if (compound.contains("Depth")) this.setDepth(compound.getByte("Depth"));

        //this.entityData.set(DATA_FROZEN_SIZE, compound.getBoolean("FrozenSize"));
    }

    @Override
    public void tick() {
        if (!isWaxed() && tickCount%250 == 0) {
            BlockPos touched = null;
            if (this.onGround()) {
                touched = getOnPos();
            } else if (this.isClimbing()) {
                Direction facing = this.getClimbingDirection();
                touched = BlockPos.containing(this.getBoundingBox().getCenter())
                        .relative(facing);
            }
            if (touched != null) {
                BlockState b = this.level().getBlockState(touched);
                if (!b.isAir()) {
                    int col = b.getMapColor(this.level(), touched).col;
                    setBodyColor(FastColor.ARGB32.lerp(0.1f, getBodyColor().getRGB(), col));
                    grow();
                }
            }
        }

        if (getFeaturePlan()==null) {
            List<AABB> base = List.of(new AABB(-getWidth()/2f, -getHeight()/2f, -getDepth()/2f, getWidth()/2f, getHeight()/2f, getDepth()/2f));
            setFeaturePlan(TrinketGrowth.generate(base, RandomSource.create(this.getFeaturePlanSeed())));
        }

        super.tick();
    }

    public void grow() {
        if (this.level().isClientSide() || !this.isOrientationSettled()) {
            return;
        }
        switch(level().random.nextInt(3)) {
            case 0: {
                if (getWidth() < 16) setWidth((byte) (getWidth() + 1));
                return;
            }
            case 1: {
                if (getHeight() < 16) setHeight((byte) (getHeight() + 1));
                return;
            }
            case 2: {
                if (getDepth() < 16) setDepth((byte) (getDepth() + 1));
                return;
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_DEPTH.equals(key) || DATA_HEIGHT.equals(key) || DATA_WIDTH.equals(key)) {
            this.applyShape();
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();

        if (item instanceof DyeItem) {
            DyeItem dyeitem = (DyeItem)item;
            DyeColor dyecolor = dyeitem.getDyeColor();
            if (dyecolor != this.getDyeColor()) {
                this.setDyeColor(dyecolor);
                itemstack.consume(1, player);
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }

        return bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand));
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem blockItem)) return super.interactAt(player, vec, hand);

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 end = eyePos.add(lookVec.scale(5));

        AABB aabb = this.getBoundingBox();
        Vec3 hitVec = aabb.clip(eyePos, end).orElse(null);
        if (hitVec == null) return super.interactAt(player, vec, hand);
        Direction face = getHitDirection(hitVec, aabb);
        if (face == null) return super.interactAt(player, vec, hand);

        BlockPos pos = this.blockPosition().offset(face.getStepX(), face.getStepY(), face.getStepZ());

        if (!player.mayUseItemAt(pos, face, stack)) return super.interactAt(player, vec, hand);;
        if (!level().mayInteract(player, pos)) return super.interactAt(player, vec, hand);;
        if (!level().getBlockState(pos).canBeReplaced()) return super.interactAt(player, vec, hand);;

        BlockPlaceContext context = new BlockPlaceContext(player, hand, stack,
                new BlockHitResult(hitVec, face, pos, false));

        if (!level().isClientSide) {
            InteractionResult result = blockItem.place(context);

            if (result.consumesAction()) {
                BlockState placed = level().getBlockState(pos);
                SoundType sound = placed.getSoundType();
                level().playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);

                return InteractionResult.SUCCESS;
            }
        }
        return super.interactAt(player, vec, hand);
    }

    private Direction getHitDirection(Vec3 hit, AABB box) {
        double dx = hit.x - box.minX;
        double dy = hit.y - box.minY;
        double dz = hit.z - box.minZ;

        double maxDx = box.maxX - box.minX;
        double maxDy = box.maxY - box.minY;
        double maxDz = box.maxZ - box.minZ;

        double epsilon = 1e-4;

        if (Math.abs(dx) < epsilon) return Direction.WEST;
        if (Math.abs(dx - maxDx) < epsilon) return Direction.EAST;
        if (Math.abs(dy) < epsilon) return Direction.DOWN;
        if (Math.abs(dy - maxDy) < epsilon) return Direction.UP;
        if (Math.abs(dz) < epsilon) return Direction.NORTH;
        if (Math.abs(dz - maxDz) < epsilon) return Direction.SOUTH;

        return null;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }

    @Override
    public boolean prefersLowStep() {
        return this.usesOrientedCollision();
    }

    @Override
    protected VoxelShape generateShape(final RandomSource random, final boolean entropic) {
        if (getWidth() == (byte) 1 || getDepth() == (byte) 1 || getHeight() == (byte) 1) {
            int[] size = SILHOUETTES[random.nextInt(SILHOUETTES.length)];
            double w = size[0] / 16.0;
            double h = size[1] / 16.0;
            double d = size[2] / 16.0;

            setWidth((byte) (w*16));
            setHeight((byte) (h*16));
            setDepth((byte) (d*16));

            return Shapes.box(0.0, 0.0, 0.0, w, h, d);
        }
        return Shapes.box(0.0, 0.0, 0.0, getWidth()/16f, getHeight()/16f, getDepth()/16f);
    }

    @Override
    public boolean fromBucket() {
        return false;
    }

    @Override
    public void setFromBucket(boolean b) {

    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        Bucketable.saveDefaultDataToBucketTag(this, stack);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, (compound) -> {
            compound.putByte("DyeColor", (byte)this.getDyeColor().getId());
            compound.putInt("BodyColor", this.getBodyColor().getRGB());
            compound.putBoolean("IsWaxed", this.isWaxed());
            compound.putString("Variant", this.getVariant());
            compound.putByte("Width", (byte) this.getWidth());
            compound.putByte("Height", (byte) this.getHeight());
            compound.putByte("Depth", (byte) this.getDepth());

            compound.putInt("shape_seed", this.entityData.get(DATA_SHAPE_SEED));
            compound.putInt("feature_seed", this.entityData.get(DATA_FEATURE_SEED));
            compound.putInt("growth_stage", this.entityData.get(DATA_GROWTH));
            compound.putByte("orientation", this.entityData.get(DATA_ORIENTATION));
            compound.putBoolean("orientation_settled", this.entityData.get(DATA_ORIENTATION_SETTLED));

            ListTag stored = new ListTag();
            stored.add(FloatTag.valueOf(this.rotation.x()));
            stored.add(FloatTag.valueOf(this.rotation.y()));
            stored.add(FloatTag.valueOf(this.rotation.z()));
            stored.add(FloatTag.valueOf(this.rotation.w()));
            compound.put("rotation", stored);

        });
    }

    @Override
    public void loadFromBucketTag(CompoundTag input) {
        Bucketable.loadDefaultDataFromBucketTag(this, input);

        if (input.contains("feature_seed")) this.entityData.set(DATA_FEATURE_SEED, input.getInt("feature_seed"));
        if (input.contains("growth_stage")) this.entityData.set(DATA_GROWTH, Mth.clamp(input.getInt("growth_stage"), 0, 100));
        if (input.contains("shape_seed")) this.entityData.set(DATA_SHAPE_SEED, input.getInt("shape_seed"));
        if (input.contains("orientation")) this.entityData.set(DATA_ORIENTATION, LivingBlockOrientation.of(input.getByte("orientation")).index());
        if (input.contains("orientation_settled")) this.entityData.set(DATA_ORIENTATION_SETTLED, input.getBoolean("orientation_settled"));

        if (!this.rotationRestored) {
            this.rotationRestored = true;
            ListTag stored = input.getList("rotation", Tag.TAG_FLOAT);
            if (stored.size() == 4) {
                this.rotation.set(stored.getFloat(0), stored.getFloat(1), stored.getFloat(2), stored.getFloat(3));
            } else {
                this.rotation.set(this.getOrientation().quaternion());
            }
            if (!isUsableRotation(this.rotation)) this.rotation.set(this.getOrientation().quaternion());

            this.rotation.normalize();
            this.lastRotation.set(this.rotation);
            this.drawnFrom.set(this.rotation);
        }

        if (input.contains("DyeColor")) this.setDyeColor(DyeColor.byId(input.getInt("DyeColor")));
        if (input.contains("BodyColor")) this.setBodyColor(input.getInt("BodyColor"));
        if (input.contains("IsWaxed")) this.setWaxed(input.getBoolean("IsWaxed"));
        if (input.contains("Variant")) this.setVariant(input.getString("Variant"));
        if (input.contains("Height")) this.setHeight(input.getByte("Height"));
        if (input.contains("Width")) this.setWidth(input.getByte("Width"));
        if (input.contains("Depth")) this.setDepth(input.getByte("Depth"));
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(BeyondItems.TRINKET_BUCKET.get());
    }

    @Override
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_TADPOLE;
    }

    static <T extends LivingEntity & Bucketable> Optional<InteractionResult> bucketMobPickup(Player player, InteractionHand hand, T entity) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (itemstack.getItem() == Items.BUCKET && entity.isAlive()) {
            entity.playSound(((Bucketable)entity).getPickupSound(), 1.0F, 1.0F);
            ItemStack itemstack1 = ((Bucketable)entity).getBucketItemStack();
            ((Bucketable)entity).saveToBucketTag(itemstack1);
            ItemStack itemstack2 = ItemUtils.createFilledResult(itemstack, player, itemstack1, false);
            player.setItemInHand(hand, itemstack2);
            Level level = entity.level();
            if (!level.isClientSide) {
                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer)player, itemstack1);
            }

            entity.discard();
            return Optional.of(InteractionResult.sidedSuccess(level.isClientSide));
        } else {
            return Optional.empty();
        }
    }
}
