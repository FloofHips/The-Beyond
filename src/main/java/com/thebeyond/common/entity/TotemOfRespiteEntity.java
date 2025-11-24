package com.thebeyond.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
import java.util.UUID;

public class TotemOfRespiteEntity extends Projectile implements TraceableEntity, ContainerEntity {
    private NonNullList<ItemStack> itemStacks;
    @Nullable
    private ResourceKey<LootTable> lootTable;
    public int timer = 0;
    public TotemOfRespiteEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
        this.itemStacks = NonNullList.createWithCapacity(27);
    }

    public void fillInventory(Collection<ItemEntity> stacks) {
        this.itemStacks = NonNullList.createWithCapacity(((Player) getOwner()).getInventory().getContainerSize());

        for ( ItemEntity stack : stacks ) {
            if (stack.getItem().isEmpty())
                continue;
            itemStacks.add(stack.getItem());
        }
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && source.getEntity().getUUID().equals(getOwner().getUUID())) {
            Containers.dropContents(this.level(), this, this);
            kill();
        }

        return super.hurt(source, amount);
    }

    public boolean triggerTimer() {
        timer = 100;
        return true;
    }

    @Override
    public void tick() {

        //if (getOwner() == null) {
        //    getOwnerUUID();
        //}

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
    protected Entity.MovementEmission getMovementEmission() {
        return super.getMovementEmission();
    }

    public boolean mayInteract(Level level, BlockPos pos) {
        //Entity entity = this.getOwner();
        //return entity instanceof Player ? entity.mayInteract(level, pos) : entity == null || EventHooks.canEntityGrief(level, entity);
        return true;
    }

    public boolean isPickable() {
        return true;
    }


    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected double getDefaultGravity() {
        return super.getDefaultGravity();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(compoundTag, this.itemStacks, this.registryAccess());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        ContainerHelper.saveAllItems(compoundTag, this.itemStacks, this.registryAccess());
    }

    @Nullable
    @Override
    public ResourceKey<LootTable> getLootTable() {
        return null;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> resourceKey) {

    }

    @Override
    public long getLootTableSeed() {
        return 0;
    }

    @Override
    public void setLootTableSeed(long l) {

    }

    @Override
    public NonNullList<ItemStack> getItemStacks() {
        return this.itemStacks;
    }

    @Override
    public void clearItemStacks() {
        this.getItemStacks().clear();
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public ItemStack getItem(int i) {
        return this.itemStacks.get(i);
    }

    @Override
    public ItemStack removeItem(int i, int amount) {
        ItemStack stack = this.getItem(i);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (stack.getCount() <= amount) {
            this.setItem(i, ItemStack.EMPTY);
            return stack;
        }

        ItemStack result = stack.split(amount);
        this.setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        ItemStack stack = this.getItem(i);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.setItem(i, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        this.itemStacks.set(i, stack);
        if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public void setChanged() {

    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        this.getItemStacks().clear();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return null;
    }
}
