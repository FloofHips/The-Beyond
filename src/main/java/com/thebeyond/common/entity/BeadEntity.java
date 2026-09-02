package com.thebeyond.common.entity;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionShapes;
import com.thebeyond.common.entity.util.livingblock.TrinketGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class BeadEntity extends LivingBlock {
    private static final EntityDataAccessor<Integer> DATA_DYE_COLOR = SynchedEntityData.defineId(BeadEntity .class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BODY_COLOR = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WAXED = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_FROZEN_SIZE = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BOOLEAN);

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

    public static byte[][] splitArray(byte[] array, int x, int y) {
        byte[][] array2d = new byte[x][y];
        int center = 7;
        int startRow = center - (y / 2);
        int startCol = center - (x / 2);

        startRow = Math.max(0, Math.min(startRow, 16 - y));
        startCol = Math.max(0, Math.min(startCol, 16 - x));

        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                int index = (startRow + j) * 16 + (startCol + i);
                array2d[i][j] = array[index];
            }
        }

        return array2d;
    }

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
        entityData.define(DATA_FROZEN_SIZE, false);

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
        compound.putBoolean("FrozenSize", this.entityData.get(DATA_FROZEN_SIZE));

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

        this.entityData.set(DATA_FROZEN_SIZE, compound.getBoolean("FrozenSize"));
    }

    @Override
    public void tick() {
        if (!isWaxed()) {
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

        if (tickCount%20 == 0 && getFeaturePlan()==null) {
            List<AABB> base = List.of(new AABB(-getWidth()/2f, -getHeight()/2f, -getDepth()/2f, getWidth()/2f, getHeight()/2f, getDepth()/2f));
            setFeaturePlan(TrinketGrowth.generate(base, RandomSource.create(this.getFeaturePlanSeed())));
        }

        super.tick();
    }

    public void grow() {
        if (this.entityData.get(DATA_FROZEN_SIZE)) {
            return;
        }
        if (this.level().isClientSide() || !this.isOrientationSettled()) {
            return;
        }
        if (tickCount%20 != 0) {
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

    private int blockerIn(final AABB slab) {
        for (LivingBlock other : this.level().getEntitiesOfClass(LivingBlock.class, slab,
                candidate -> candidate != this && candidate.isAlive())) {
            LivingBlockCollisionShapes.Placement placement =
                    LivingBlockCollisionShapes.preciseGeometry(other);
            if (placement == null) {
                return other.getId();
            }
            for (AABB box : placement.boxes()) {
                if (box.intersects(slab)) {
                    return other.getId();
                }
            }
        }
        return -1;
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
            //if (this.isOwnedBy(player)) {
                DyeColor dyecolor = dyeitem.getDyeColor();
                if (dyecolor != this.getDyeColor()) {
                    this.setDyeColor(dyecolor);
                    itemstack.consume(1, player);
                    return InteractionResult.SUCCESS;
                }

                return super.mobInteract(player, hand);
            //}
        }

        return super.mobInteract(player, hand);
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
}
