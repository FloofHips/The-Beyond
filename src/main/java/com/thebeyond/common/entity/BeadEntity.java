package com.thebeyond.common.entity;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
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
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_DYE_COLOR, DyeColor.WHITE.getId());
        entityData.define(DATA_BODY_COLOR, Color.WHITE.getRGB());
        entityData.define(DATA_WAXED, false);
        entityData.define(DATA_VARIANT, "swirl");
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("DyeColor", (byte)this.getDyeColor().getId());
        compound.putInt("BodyColor", this.getBodyColor().getRGB());
        compound.putBoolean("IsWaxed", this.isWaxed());
        compound.putString("Variant", this.getVariant());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setDyeColor(DyeColor.byId(compound.getInt("DyeColor")));
        this.setBodyColor(compound.getInt("BodyColor"));
        this.setWaxed(compound.getBoolean("IsWaxed"));
        this.setVariant(compound.getString("Variant"));
    }

    @Override
    public void tick() {
        if (!isWaxed() && this.onGround()) {
            BlockPos onPos = getOnPos();
            BlockState b = this.level().getBlockState(onPos);
            int col = b.getMapColor(this.level(), onPos).col;

            if (!b.isAir()) {
                setBodyColor(FastColor.ARGB32.lerp(0.1f, getBodyColor().getRGB(), col));
                grow();
            }
        }
            super.tick();
    }

    private boolean isStill() {
        return xOld == getX() && yOld == getY() && zOld == getZ();
    }

    public void grow() {
        VoxelShape shape = getCustomShape();
        int i = level().random.nextInt(6);

        double minx = shape.min(Direction.Axis.X);
        double maxx = shape.max(Direction.Axis.X);
        double miny = shape.min(Direction.Axis.Y);
        double maxy = shape.max(Direction.Axis.Y);
        double minz = shape.min(Direction.Axis.Z);
        double maxz = shape.max(Direction.Axis.Z);

        switch (i) {
            case 0: { minx = Math.max(0, minx - level().random.nextInt(2)/16f); break; }
            case 1: { miny = Math.max(0, miny - level().random.nextInt(2)/16f); break; }
            case 2: { minz = Math.max(0, minz - level().random.nextInt(2)/16f); break; }
            case 3: { maxx = Math.min(1, maxx + level().random.nextInt(2)/16f); break; }
            case 4: { maxy = Math.min(1, maxy + level().random.nextInt(2)/16f); break; }
            case 5: { maxz = Math.min(1, maxz + level().random.nextInt(2)/16f); break; }
        }

        this.setShape(Shapes.box(minx, miny, minz, maxx, maxy, maxz));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();

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
    protected VoxelShape generateShape(final RandomSource random, final boolean entropic) {
        int[] size = SILHOUETTES[random.nextInt(SILHOUETTES.length)];
        double w = size[0] / 16.0;
        double h = size[1] / 16.0;
        double d = size[2] / 16.0;

        if (!entropic) {
            return Shapes.box(0.0, 0.0, 0.0, w, h, d);
        }

        AABB core = new AABB((1.0 - w) * 0.5, (1.0 - h) * 0.5, (1.0 - d) * 0.5,
                (1.0 + w) * 0.5, (1.0 + h) * 0.5, (1.0 + d) * 0.5);
        return LivingBlockShapeFactory.growEntropicFrom(random, core);
    }
}
