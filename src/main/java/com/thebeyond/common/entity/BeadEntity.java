package com.thebeyond.common.entity;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionShapes;
import com.thebeyond.common.entity.util.livingblock.LivingBlockShapeFactory;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Optional;

public class BeadEntity extends LivingBlock {
    private static final EntityDataAccessor<Integer> DATA_DYE_COLOR = SynchedEntityData.defineId(BeadEntity .class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BODY_COLOR = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WAXED = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_BOX_GROWTH = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.INT);

    private static final org.slf4j.Logger GROWTH_LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final int GROWTH_FACES = 6;
    private static final int GROWTH_LOG_INTERVAL = 40;
    private static final double GROWTH_STEP = 1.0 / 16.0;
    private static final int GROWTH_BITS = 4;
    private static final int GROWTH_MAX = 15;

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
        entityData.define(DATA_BOX_GROWTH, 0);
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("DyeColor", (byte)this.getDyeColor().getId());
        compound.putInt("BodyColor", this.getBodyColor().getRGB());
        compound.putBoolean("IsWaxed", this.isWaxed());
        compound.putString("Variant", this.getVariant());
        compound.putInt("BoxGrowth", this.entityData.get(DATA_BOX_GROWTH));
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setDyeColor(DyeColor.byId(compound.getInt("DyeColor")));
        this.setBodyColor(compound.getInt("BodyColor"));
        this.setWaxed(compound.getBoolean("IsWaxed"));
        this.setVariant(compound.getString("Variant"));
        this.entityData.set(DATA_BOX_GROWTH, compound.getInt("BoxGrowth"));
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
        if (this.level().isClientSide() || !this.isOrientationSettled()) {
            return;
        }
        int face = level().random.nextInt(GROWTH_FACES);
        if (level().random.nextInt(2) == 0) {
            return;
        }
        int growth = this.entityData.get(DATA_BOX_GROWTH);
        int steps = (growth >> (face * GROWTH_BITS)) & GROWTH_MAX;
        if (steps >= GROWTH_MAX) {
            return;
        }
        int blocker = face >= 3 ? this.growthBlocker(face) : -1;
        if (blocker >= 0) {
            if (this.tickCount % GROWTH_LOG_INTERVAL == 0) {
                AABB hull = this.getBoundingBox();
                GROWTH_LOGGER.debug("[livingblock] grow id={} face={} steps={} refused={} size={}",
                        this.getId(), face, steps, blocker,
                        String.format("%.3f,%.3f,%.3f", hull.getXsize(), hull.getYsize(), hull.getZsize()));
            }
            return;
        }
        this.entityData.set(DATA_BOX_GROWTH,
                (growth & ~(GROWTH_MAX << (face * GROWTH_BITS))) | ((steps + 1) << (face * GROWTH_BITS)));
    }

    private int growthBlocker(final int face) {
        AABB hull = this.getBoundingBox();
        Direction.Axis axis = this.getOrientation()
                .worldAxisOf(Direction.Axis.values()[face - 3]);
        if (axis == Direction.Axis.Y) {
            return this.blockerIn(new AABB(hull.minX, hull.maxY, hull.minZ,
                    hull.maxX, hull.maxY + GROWTH_STEP, hull.maxZ));
        }
        return this.blockerIn(axis == Direction.Axis.X
                ? hull.inflate(GROWTH_STEP, 0.0, 0.0)
                : hull.inflate(0.0, 0.0, GROWTH_STEP));
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
        if (DATA_BOX_GROWTH.equals(key)) {
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
        return true;
    }

    @Override
    protected VoxelShape generateShape(final RandomSource random, final boolean entropic) {
        int[] size = SILHOUETTES[random.nextInt(SILHOUETTES.length)];
        double w = size[0] / 16.0;
        double h = size[1] / 16.0;
        double d = size[2] / 16.0;

        if (!entropic) {
            return this.grown(Shapes.box(0.0, 0.0, 0.0, w, h, d));
        }

        AABB core = new AABB((1.0 - w) * 0.5, (1.0 - h) * 0.5, (1.0 - d) * 0.5,
                (1.0 + w) * 0.5, (1.0 + h) * 0.5, (1.0 + d) * 0.5);
        return LivingBlockShapeFactory.growEntropicFrom(random, core);
    }

    private VoxelShape grown(final VoxelShape base) {
        int growth = this.entityData.get(DATA_BOX_GROWTH);
        if (growth == 0) {
            return base;
        }
        double[] face = new double[GROWTH_FACES];
        for (int i = 0; i < GROWTH_FACES; i++) {
            face[i] = ((growth >> (i * GROWTH_BITS)) & GROWTH_MAX) / 16.0;
        }
        return Shapes.box(
                Math.max(0.0, base.min(Direction.Axis.X) - face[0]),
                Math.max(0.0, base.min(Direction.Axis.Y) - face[1]),
                Math.max(0.0, base.min(Direction.Axis.Z) - face[2]),
                Math.min(1.0, base.max(Direction.Axis.X) + face[3]),
                Math.min(1.0, base.max(Direction.Axis.Y) + face[4]),
                Math.min(1.0, base.max(Direction.Axis.Z) + face[5]));
    }
}
