package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.registry.BeyondMenus;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Photos fire only on the redstone rising edge, never from this GUI. */
public class CameraBlockMenu extends AbstractContainerMenu {
    private static final int CAMERA_SLOTS = CameraBlockEntity.SLOTS;

    private final Container container;
    private final BlockPos blockPos;

    // Client side: IMenuTypeExtension hands back the buf written at open time.
    public CameraBlockMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    private CameraBlockMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, resolveClientContainer(pos), pos);
    }

    private static Container resolveClientContainer(BlockPos pos) {
        BlockEntity be = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getBlockEntity(pos) : null;
        return be instanceof CameraBlockEntity camera ? camera : new SimpleContainer(CAMERA_SLOTS);
    }

    public CameraBlockMenu(int containerId, Inventory playerInventory, Container container, BlockPos pos) {
        super(BeyondMenus.CAMERA_BLOCK.get(), containerId);
        this.blockPos = pos;
        this.container = container;
        checkContainerSize(this.container, CAMERA_SLOTS);

        this.addSlot(new Slot(this.container, CameraBlockEntity.FILM, 71, 7) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(BeyondTags.CAMERA_FILM);
            }

            @Override
            public int getMaxStackSize() {
                return CameraBlockEntity.MAX_FILM;
            }
        });

        // Fuel tag is empty for now, so this slot stays inert.
        this.addSlot(new Slot(this.container, CameraBlockEntity.FUEL, 89, 7) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(BeyondTags.CAMERA_FUEL);
            }
        });

        for (int k = 0; k < 3; ++k) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + k * 9 + 9, 8 + l * 18, 45 + k * 18));
            }
        }

        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 105));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack moving = slot.getItem();
            result = moving.copy();
            int invStart = CAMERA_SLOTS;
            int invEnd = CAMERA_SLOTS + 36;          // past the hotbar
            if (index < CAMERA_SLOTS) {
                if (!this.moveItemStackTo(moving, invStart, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (moving.is(BeyondTags.CAMERA_FILM)) {
                    if (!this.moveItemStackTo(moving, CameraBlockEntity.FILM, CameraBlockEntity.FILM + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (moving.is(BeyondTags.CAMERA_FUEL)) {
                    if (!this.moveItemStackTo(moving, CameraBlockEntity.FUEL, CameraBlockEntity.FUEL + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (moving.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (moving.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, moving);
        }
        return result;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }
}
