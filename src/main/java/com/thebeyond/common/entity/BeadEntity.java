package com.thebeyond.common.entity;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockShapeFactory;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;
import java.util.Optional;

public class BeadEntity extends LivingBlock {
    private static final EntityDataAccessor<Integer> DATA_DYE_COLOR = SynchedEntityData.defineId(BeadEntity .class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BODY_COLOR = SynchedEntityData.defineId(BeadEntity.class, EntityDataSerializers.INT);

    public DyeColor getDyeColor() {
        return DyeColor.byId((Integer)this.entityData.get(DATA_DYE_COLOR));
    }
    private void setDyeColor(DyeColor dye) {
        this.entityData.set(DATA_DYE_COLOR, dye.getId());
    }
    public Color getBodyColor() {return new Color(this.entityData.get(DATA_BODY_COLOR));}
    private void setBodyColor(int color) {this.entityData.set(DATA_BODY_COLOR, color);}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_DYE_COLOR, DyeColor.WHITE.getId());
        entityData.define(DATA_BODY_COLOR, Color.WHITE.getRGB());
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("DyeColor", (byte)this.getDyeColor().getId());
        compound.putInt("BodyColor", this.getBodyColor().getRGB());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("DyeColor", 99)) {
            this.setDyeColor(DyeColor.byId(compound.getInt("DyeColor")));
        }
        if (compound.contains("BodyColor")) {
            this.setBodyColor(compound.getInt("BodyColor"));
        }
    }

    @Override
    public void tick() {
        if (this.getDeltaMovement().length() > 0 && this.onGround()) {
            BlockPos onPos = getOnPos();
            BlockState b = this.level().getBlockState(onPos);
            int col = b.getMapColor(this.level(), onPos).col;

            if (!b.isAir())
                setBodyColor((int) FastColor.ARGB32.lerp(0.1f, getBodyColor().getRGB(), col));
        }
            super.tick();
    }

    private static final int[][] SILHOUETTES = {
            {4, 4, 4},
            {4, 12, 4},
            {8, 12, 4},
            {8, 8, 8}
    };

    public BeadEntity(final EntityType<? extends Mob> type, final Level level) {
        super(type, level);
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
