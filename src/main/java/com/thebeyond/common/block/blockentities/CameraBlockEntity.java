package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CameraBlockEntity extends BlockEntity implements Container, MenuProvider {
    // Slot layout and film cap live in CameraSlots (the shared source); re-exposed here for this container's API and slot overrides.
    public static final int SLOTS = CameraSlots.SLOTS;
    public static final int FILM = CameraSlots.FILM;
    public static final int FUEL = CameraSlots.FUEL;
    public static final int MAX_FILM = CameraSlots.MAX_FILM;

    private static final Component DEFAULT_NAME = Component.translatable("container.the_beyond.camera");

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private UUID owner = Util.NIL_UUID;
    private long lastCaptureTick = Long.MIN_VALUE;

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(BeyondBlockEntities.CAMERA.get(), pos, state);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = (owner == null) ? Util.NIL_UUID : owner;
        setChanged();
    }

    public long getLastCaptureTick() {
        return lastCaptureTick;
    }

    public void setLastCaptureTick(long tick) {
        this.lastCaptureTick = tick;
        setChanged();
    }

    public int filmCount() {
        return items.get(FILM).getCount();
    }

    public boolean hasFilm() {
        return filmCount() > 0;
    }

    public void consumeFilm() {
        ItemStack film = items.get(FILM);
        if (!film.isEmpty()) {
            film.shrink(1);
            setChanged();
        }
    }

    public ItemContainerContents toContainerContents() {
        return ItemContainerContents.fromItems(items);
    }

    // Dormant: uncomment with the gate in CameraBlock.fireCapture once #thebeyond:camera_fuel is populated.
    // public int fuelCount() {
    //     return items.get(FUEL).getCount();
    // }
    // public boolean hasFuel() {
    //     return fuelCount() > 0;
    // }
    // public void consumeFuel() {
    //     ItemStack fuel = items.get(FUEL);
    //     if (!fuel.isEmpty()) {
    //         fuel.shrink(1);
    //         setChanged();
    //     }
    // }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public int getMaxStackSize() {
        return MAX_FILM;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack s = ContainerHelper.removeItem(items, slot, amount);
        if (!s.isEmpty()) {
            setChanged();
        }
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case FILM -> stack.is(BeyondTags.CAMERA_FILM);
            case FUEL -> stack.is(BeyondTags.CAMERA_FUEL); // empty tag accepts nothing yet
            default -> false;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        clearSlots();
    }

    // items is fixed-size, so clear() would throw.
    private void clearSlots() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
    }

    // Block update on every change so the open GUI reflects hopper edits too.
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clearSlots(); // loadAllItems skips absent slots; reset first so emptied slots clear on the client
        ContainerHelper.loadAllItems(tag, items, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : Util.NIL_UUID;
        lastCaptureTick = tag.getLong("LastCapture");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putUUID("Owner", owner);
        tag.putLong("LastCapture", lastCaptureTick);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        clearSlots();
        componentInput.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
    }

    @Override
    public Component getDisplayName() {
        return DEFAULT_NAME;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CameraBlockMenu(containerId, inventory, this, this.getBlockPos());
    }
}
