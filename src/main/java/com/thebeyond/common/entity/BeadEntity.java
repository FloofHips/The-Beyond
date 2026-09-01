package com.thebeyond.common.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionShapes;
import com.thebeyond.common.registry.BeyondEntityDataSerializers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
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

public class BeadEntity extends LivingBlock {
    private static final EntityDataAccessor<Integer> DATA_DYE_COLOR = SynchedEntityData.defineId(BeadEntity .class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BODY_COLOR = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WAXED = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_FROZEN_SIZE = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Byte> DATA_WIDTH = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_HEIGHT = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_DEPTH = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BYTE);

    private static final EntityDataAccessor<BeyondEntityDataSerializers.TrinketGrowth> DATA_GROWTH_UP = SynchedEntityData.defineId(BeadEntity.class,    BeyondEntityDataSerializers.TRINKET_GROWTH.get());
    private static final EntityDataAccessor<BeyondEntityDataSerializers.TrinketGrowth> DATA_GROWTH_DOWN = SynchedEntityData.defineId(BeadEntity.class,  BeyondEntityDataSerializers.TRINKET_GROWTH.get());
    private static final EntityDataAccessor<BeyondEntityDataSerializers.TrinketGrowth> DATA_GROWTH_WEST = SynchedEntityData.defineId(BeadEntity.class,  BeyondEntityDataSerializers.TRINKET_GROWTH.get());
    private static final EntityDataAccessor<BeyondEntityDataSerializers.TrinketGrowth> DATA_GROWTH_EAST = SynchedEntityData.defineId(BeadEntity.class,  BeyondEntityDataSerializers.TRINKET_GROWTH.get());
    private static final EntityDataAccessor<BeyondEntityDataSerializers.TrinketGrowth> DATA_GROWTH_NORTH = SynchedEntityData.defineId(BeadEntity.class, BeyondEntityDataSerializers.TRINKET_GROWTH.get());
    private static final EntityDataAccessor<BeyondEntityDataSerializers.TrinketGrowth> DATA_GROWTH_SOUTH = SynchedEntityData.defineId(BeadEntity.class, BeyondEntityDataSerializers.TRINKET_GROWTH.get());

    private static final BeyondEntityDataSerializers.TrinketGrowth DEFAULT = new BeyondEntityDataSerializers.TrinketGrowth(new byte[]{
            -2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,-3,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,-1,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,-1,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,-2,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 });

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

    public int getHeight() {return this.entityData.get(DATA_HEIGHT);}
    private void setHeight(byte height) {this.entityData.set(DATA_HEIGHT, height);}
    public int getWidth() {return this.entityData.get(DATA_WIDTH);}
    private void setWidth(byte width) {this.entityData.set(DATA_WIDTH, width);}
    public int getDepth() {return this.entityData.get(DATA_DEPTH);}
    private void setDepth(byte depth) {this.entityData.set(DATA_DEPTH, depth);}


    public byte[][] getGrowth(Direction direction) {
        switch (direction) {
            case UP -> splitArray(this.entityData.get(DATA_GROWTH_UP).growths(), getWidth(), getDepth());
            case DOWN -> splitArray(this.entityData.get(DATA_GROWTH_DOWN).growths(), getWidth(), getDepth());
            case WEST -> splitArray(this.entityData.get(DATA_GROWTH_WEST).growths(), getDepth(), getHeight());
            case EAST -> splitArray(this.entityData.get(DATA_GROWTH_EAST).growths(), getDepth(), getHeight());
            case NORTH -> splitArray(this.entityData.get(DATA_GROWTH_NORTH).growths(), getWidth(), getHeight());
            case SOUTH -> splitArray(this.entityData.get(DATA_GROWTH_SOUTH).growths(), getWidth(), getHeight());
        }
        return splitArray(this.entityData.get(DATA_GROWTH_UP).growths(), getWidth(), getDepth());
    }

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

        entityData.define(DATA_GROWTH_UP,    DEFAULT);
        entityData.define(DATA_GROWTH_DOWN,  DEFAULT);
        entityData.define(DATA_GROWTH_WEST,  DEFAULT);
        entityData.define(DATA_GROWTH_EAST,  DEFAULT);
        entityData.define(DATA_GROWTH_NORTH, DEFAULT);
        entityData.define(DATA_GROWTH_SOUTH, DEFAULT);
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

        compound.putByteArray("GrowthUp", this.entityData.get(DATA_GROWTH_UP).growths());
        compound.putByteArray("GrowthDown", this.entityData.get(DATA_GROWTH_DOWN).growths());
        compound.putByteArray("GrowthWest", this.entityData.get(DATA_GROWTH_WEST).growths());
        compound.putByteArray("GrowthEast", this.entityData.get(DATA_GROWTH_EAST).growths());
        compound.putByteArray("GrowthNorth", this.entityData.get(DATA_GROWTH_NORTH).growths());
        compound.putByteArray("GrowthSouth", this.entityData.get(DATA_GROWTH_SOUTH).growths());
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

        readGrowthData(compound, "GrowthUp", DATA_GROWTH_UP);
        readGrowthData(compound, "GrowthDown", DATA_GROWTH_DOWN);
        readGrowthData(compound, "GrowthWest", DATA_GROWTH_WEST);
        readGrowthData(compound, "GrowthEast", DATA_GROWTH_EAST);
        readGrowthData(compound, "GrowthNorth", DATA_GROWTH_NORTH);
        readGrowthData(compound, "GrowthSouth", DATA_GROWTH_SOUTH);
    }

    public <T> void readGrowthData(CompoundTag compound, String data, EntityDataAccessor<BeyondEntityDataSerializers.TrinketGrowth> key) {
        if (compound.contains(data)) {
            byte[] s = compound.getByteArray(data);
            this.entityData.set(key, new BeyondEntityDataSerializers.TrinketGrowth(s));
        }
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

        //int growth = this.entityData.get(DATA_BOX_GROWTH);
        //int steps = (growth >> (face * GROWTH_BITS)) & GROWTH_MAX;
        //if (steps >= GROWTH_MAX) {
        //    return;
        //}
        //int blocker = face >= 3 ? this.growthBlocker(face) : -1;
        //if (blocker >= 0) {
        //    if (this.tickCount % GROWTH_LOG_INTERVAL == 0) {
        //        AABB hull = this.getBoundingBox();
        //        if (shouldLog) GROWTH_LOGGER.debug("[livingblock] grow id={} face={} steps={} refused={} size={}",
        //                this.getId(), face, steps, blocker,
        //                String.format("%.3f,%.3f,%.3f", hull.getXsize(), hull.getYsize(), hull.getZsize()));
        //    }
        //    return;
        //}
        //this.entityData.set(DATA_BOX_GROWTH,
        //        (growth & ~(GROWTH_MAX << (face * GROWTH_BITS))) | ((steps + 1) << (face * GROWTH_BITS)));
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

    //private VoxelShape grown(final VoxelShape base) {
    //    int growth = this.entityData.get(DATA_BOX_GROWTH);
    //    if (growth == 0) {
    //        return base;
    //    }
    //    double[] face = new double[GROWTH_FACES];
    //    for (int i = 0; i < GROWTH_FACES; i++) {
    //        face[i] = ((growth >> (i * GROWTH_BITS)) & GROWTH_MAX) / 16.0;
    //    }
    //    return Shapes.box(
    //            Math.max(0.0, base.min(Direction.Axis.X) - face[0]),
    //            Math.max(0.0, base.min(Direction.Axis.Y) - face[1]),
    //            Math.max(0.0, base.min(Direction.Axis.Z) - face[2]),
    //            Math.min(1.0, base.max(Direction.Axis.X) + face[3]),
    //            Math.min(1.0, base.max(Direction.Axis.Y) + face[4]),
    //            Math.min(1.0, base.max(Direction.Axis.Z) + face[5]));
    //}
}
