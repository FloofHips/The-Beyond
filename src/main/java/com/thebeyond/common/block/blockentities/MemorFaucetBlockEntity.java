package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.block.MemorFaucetBlock;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MemorFaucetBlockEntity extends BlockEntity implements Container {
    private static final int CHECK_INTERVAL = 50;
    private static final int DETECTION_RANGE = 5;
    private static final int NOMAD_RANGE = 32;
    private int tickCounter = 0;
    public float activeProgress = 0;
    private boolean active = false;
    private NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);

    public MemorFaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        setItems();
    }

    public MemorFaucetBlockEntity(BlockPos pos, BlockState blockState) {
        this(BeyondBlockEntities.MEMOR_FAUCET.get(), pos, blockState);
        setItems();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MemorFaucetBlockEntity be) {

        if (state.getValue(MemorFaucetBlock.AGE) == MemorFaucetBlock.MAX_AGE && be.activeProgress == 0) return;

        if (be.active) {
            be.activeProgress = Mth.lerp(0.2f, be.activeProgress, 1);
        } else {
            be.activeProgress = Mth.lerp(0.1f, be.activeProgress, 0);
        }

        be.tickCounter++;

        if (be.tickCounter >= CHECK_INTERVAL) {
            be.tickCounter = 0;
            be.checkForActivation(level, pos);
            be.consumeItems(level, pos);
        }

        if (be.activeProgress > 0 && be.activeProgress < 1) {
            be.setChanged();
            if (!level.isClientSide) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    private void checkForActivation(Level level, BlockPos pos) {
        AABB detectionBox = new AABB(pos).inflate(DETECTION_RANGE);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, detectionBox);

        for (LivingEntity entity : entities) {
            if (shouldActivate(entity)) {
                activateFaucet(level, pos);
                break;
            } else {
                deactivateFaucet(level, pos);
                break;
            }
        }
    }

    private boolean shouldActivate(LivingEntity entity) {
        if (entity instanceof AbyssalNomadEntity) {
            return true;
        }

        if (entity instanceof Player player) {
            return player.getMainHandItem().is(BeyondTags.REMEMBRANCES) ||
                    player.getOffhandItem().is(BeyondTags.REMEMBRANCES);
        }

        return false;
    }

    private void activateFaucet(Level level, BlockPos pos) {
        setItems();
        if (active) return;
        active = true;
        level.playSound(null, pos, SoundEvents.VAULT_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.5, 0.5, 0.5, 0.1);
        }

        setChanged();
        if (!level.isClientSide) {
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
        }
    }

    private void deactivateFaucet(Level level, BlockPos pos) {
        if (!active) return;
        active = false;
        level.playSound(null, pos, SoundEvents.VAULT_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);

        setChanged();
        if (!level.isClientSide) {
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
        }
    }

    public float getActiveProgress() {
        return activeProgress;
    }

    public boolean isActive() {
        return active;
    }

    private void setItems() {
        Components.DynamicColorComponent colors = new Components.DynamicColorComponent(0.5f, 1.7f, 1.9f, 0.8f, 0, 0.2f, 0, 0.2f, 0xF000F0);

        List<Item> remembranceItems = new ArrayList<>();

        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(BeyondTags.REMEMBRANCES)) {
             remembranceItems.add(holder.value());
        }

        if (remembranceItems.isEmpty()) {
            ItemStack fallbackStack = BeyondItems.REMEMBRANCE_LACE.toStack();
            fallbackStack.set(BeyondComponents.COLOR_COMPONENT, colors);

            for (int i = 0; i < getContainerSize(); i++) {
                setItem(i, fallbackStack.copy());
            }
            return;
        } else {
            Collections.shuffle(remembranceItems);

            for (int i = 0; i < getContainerSize(); i++) {
                Item remembranceItem = remembranceItems.get(i % remembranceItems.size());
                ItemStack stack = new ItemStack(remembranceItem);

                stack.set(BeyondComponents.COLOR_COMPONENT, colors);
                setItem(i, stack);
            }
        }
    }

    private void consumeItems(Level level, BlockPos pos) {
        AABB itemBB = new AABB(pos).inflate(3.0);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, itemBB);

        for (ItemEntity itemEntity : items) {
            ItemStack itemStack = itemEntity.getItem();

            if (!itemStack.is(BeyondTags.REMEMBRANCES)) {
                continue;
            }

            if (itemStack.getCount() > 1) {
                itemStack.shrink(1);

                ItemStack remainingStack = itemStack.copy();
                itemEntity.setItem(remainingStack);
            } else {
                itemEntity.discard();
            }

            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1, 1);
            level.playSound(null, pos, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.BLOCKS, 0.5F, 1.0F);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), itemEntity.position().x, itemEntity.position().y + 0.1, itemEntity.position().z, 1, 0, 0, 0, 0);
            }

            increaseAge(level, pos);
            break;
        }
    }

    private void increaseAge(Level level, BlockPos pos) {
        BlockState currentState = level.getBlockState(pos);
        int currentAge = currentState.getValue(MemorFaucetBlock.AGE);

        if (currentAge < MemorFaucetBlock.MAX_AGE) {
            int newAge = currentAge + 1;
            level.setBlock(pos, currentState.setValue(MemorFaucetBlock.AGE, newAge), 3);

            if (newAge >= MemorFaucetBlock.MAX_AGE) {
                affectNomads(level, pos);
            }
        }
    }

    private void affectNomads(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(NOMAD_RANGE);
        List<AbyssalNomadEntity> nomads = level.getEntitiesOfClass(AbyssalNomadEntity.class, box);

        level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.BLOCKS, 1, 1);

        for (AbyssalNomadEntity nomad : nomads) {
            nomad.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0));
            level.playSound(null, nomad.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.5F, 0.8F);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), nomad.position().x, nomad.position().y + nomad.getEyeHeight() + 0.1, nomad.position().z, 1, 0, 0, 0, 0);
            }
        }
    }

    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    public int getContainerSize() {
        return 5;
    }

    @Override
    public boolean isEmpty() {
        for(ItemStack itemstack : this.getItems()) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.getItems().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemstack = ContainerHelper.removeItem(this.getItems(), slot, amount);
        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.getItems(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.getItems().set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.getItems().clear();
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }


    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        active = tag.getBoolean("Active");
        activeProgress = tag.getFloat("ActiveProgress");
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Active", active);
        tag.putFloat("ActiveProgress", activeProgress);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Active", active);
        tag.putFloat("ActiveProgress", activeProgress);
        ContainerHelper.saveAllItems(tag, items, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}