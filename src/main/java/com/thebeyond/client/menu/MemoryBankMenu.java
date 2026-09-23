package com.thebeyond.client.menu;

import com.thebeyond.client.gui.MemoryBankScreen;
import com.thebeyond.common.block.ProjectorAcceptance;
import com.thebeyond.common.item.MemoryBankItem;
import com.thebeyond.common.item.SnapshotItem;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.common.registry.BeyondMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MemoryBankMenu extends AbstractContainerMenu {
    private final Container container;

    public MemoryBankMenu(int id, Inventory playerInventory, ItemStack stack) {
        super(BeyondMenus.MEMORY_BANK.get(), id);
        this.container = new MemoryBankContainer(stack);
        addSlots(playerInventory);
    }

    private void addSlots(Inventory playerInventory) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 6; col++) {

                int index = col + row * 6;
                addSlot(new Slot(this.container, index, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack s) {
                        return s.getItem() instanceof SnapshotItem;
                    }
                });
            }
        }

        for (int row = 0; row < 9; ++row) {
            for (int col = 0; col < 3; ++col) {
                int invIndex = 9 + col + row * 3;
                int x = 187 + col * 18;
                int y = 8 + row * 18;
                this.addSlot(new Slot(playerInventory, invIndex, x, y));
            }
        }

        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 245, 8 + i * 18));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        Slot slot = slots.get(i);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack inSlot = slot.getItem();
        ItemStack copy = inSlot.copy();

        if (i < 24) {
            if (!moveItemStackTo(inSlot, 24, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!(inSlot.getItem() instanceof SnapshotItem)) return ItemStack.EMPTY;
            if (!moveItemStackTo(inSlot, 0, 24, false)) return ItemStack.EMPTY;
        }

        slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}