package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TotemOfRespiteEntity extends Entity {
    private static final EntityDataAccessor<Integer> LIFESPAN = SynchedEntityData.defineId(TotemOfRespiteEntity.class, EntityDataSerializers.INT);
    private String owner;
    private List<ItemStack> storedItems = new LinkedList<>();

    public TotemOfRespiteEntity(EntityType<? extends TotemOfRespiteEntity> entityType, Level worldIn) {
        super(entityType, worldIn);
    }

    public void addItem(ItemStack stack) {
        storedItems.add(stack);
    }
    public void setOwner(Player player) {
        owner = player.getUUID().toString();
    }

    private Player getOwner() {
        for(Player player : level().players()) {
            String id = player.getUUID().toString();
            if(id.equals(owner))
                return player;
        }
        return null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        explode();
        return super.hurt(source, amount);
    }

    public void explode() {
        NonNullList<ItemStack> items = NonNullList.withSize(storedItems.size(), ItemStack.EMPTY);

        for (int i = 0; i < storedItems.size(); i++) {
            items.set(i, storedItems.get(i));
        }

        if (items.isEmpty()) this.spawnAtLocation(new ItemStack(BeyondItems.TOTEM_OF_RESPITE.get(), 1));
        else Containers.dropContents(this.level(), this.blockPosition(), items);

        this.playSound(SoundEvents.ENDER_EYE_DEATH, 1.0F, 1.0F);

        for(int i = 0; (float)i < 16.0F; ++i) {
            float f2 = this.random.nextFloat() * ((float)Math.PI * 2F);
            float f3 = this.random.nextFloat() * 0.5F + 0.5F;
            float f4 = Mth.sin(f2) * f3;
            float f5 = Mth.cos(f2) * f3;
            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, BeyondItems.TOTEM_OF_RESPITE.toStack()), this.getX() + (double)f4, this.getY(), this.getZ() + (double)f5, (double)0.0F, (double)0.0F, (double)0.0F);
        }

        this.discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LIFESPAN, 3600);
    }

    @Override
    public void tick() {
        super.tick();
        if(!isAlive()) return;

        if(getLifeSpan()>0) {
            setLifeSpan(getLifeSpan()-1);
        } else {
            explode();
        }

        Vec3 vec3 = this.getDeltaMovement();
        double d0 = this.getX() + vec3.x;
        double d1 = this.getY() + vec3.y;
        double d2 = this.getZ() + vec3.z;

        if (level().isClientSide)
            level().addParticle(ParticleTypes.PORTAL, d0, d1 + (this.random.nextFloat() - 0.5) * 0.2, d2, this.random.nextFloat() - 0.5, this.random.nextFloat() - 0.6, this.random.nextFloat() - 0.5);

        if (this.isInWater()) {
            for(int i = 0; i < 4; ++i) {
                this.level().addParticle(ParticleTypes.BUBBLE, d0 - vec3.x * (double)0.25F, d1 - vec3.y * (double)0.25F, getZ() - vec3.z * (double)0.25F, vec3.x, vec3.y, vec3.z);
            }
        }

        if (getOwner() != null && this.level() == getOwner().level()) {
            Vec3 currentPos = this.position();
            Vec3 ownerPos = getOwner().position();
            Vec3 direction = ownerPos.subtract(currentPos);

            double distance = direction.length();

            if (distance > 2) {
                double speed = distance/20f;
                Vec3 movement = direction.normalize().scale(speed);

                this.setDeltaMovement(movement);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8));

                if (distance < 1.0) {
                    this.setDeltaMovement(Vec3.ZERO);
                }
            }

            this.hasImpulse = true;
        }

        super.tick();
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    public void setLifeSpan(int lifespan) {
        entityData.set(LIFESPAN, lifespan);
    }

    public int getLifeSpan() {
        return entityData.get(LIFESPAN);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        int lifespan = compound.getInt("Lifespan");
        entityData.set(LIFESPAN, lifespan);

        owner = compound.getString("Owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("Lifespan", entityData.get(LIFESPAN));
        if(owner != null)
            compound.putString("Owner", owner);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }
}
