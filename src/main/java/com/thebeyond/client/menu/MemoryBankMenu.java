package com.thebeyond.client.menu;

import com.thebeyond.common.item.SnapshotItem;
import com.thebeyond.common.registry.BeyondMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MemoryBankMenu extends AbstractContainerMenu {
    private final MemoryBankContainer container;
    private final ContainerData data;
    public final ItemStack stack;

    public MemoryBankMenu(int id, Inventory playerInventory, ItemStack stack) {
        super(BeyondMenus.MEMORY_BANK.get(), id);
        this.container = new MemoryBankContainer(stack);
        this.stack = stack;
        this.data = new ContainerData() {
            @Override public int get(int i) { return container.getPage(); }
            @Override public void set(int i, int v) { container.setPage(v); }
            @Override public int getCount() { return 1; }
        };
        addSlots(playerInventory);
        addDataSlots(data);
    }

    @Override
    public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
        container.setPage(data.get(MemoryBankContainer.DATA_PAGE));
        container.reload();
        super.initializeContents(stateId, items, carried);
    }

    public MemoryBankContainer getBank() { return container; }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        container.setPage(value);
        container.reload();
    }

    public void changePage(int delta) {
        int target = container.getPage() + delta;
        if (target < 0) return;
        if (target > container.getMaxPage()) return;
        container.setPage(target);
        broadcastChanges();
    }

    private void addSlots(Inventory playerInventory) {
        for (int i = 0; i < 2; i++) {
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 3; col++) {
                    int index = i * 12 + row * 3 + col;

                    int x = -68 + col * 40 + i * 128;
                    int y = 9 + row * 40;

                    addSlot(new Slot(this.container, index, x, y) {
                        @Override
                        public boolean mayPlace(ItemStack s) {
                            return s.getItem() instanceof SnapshotItem;
                        }
                    });
                }
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

        if (i < MemoryBankContainer.PAGE_SIZE) {
            if (!moveItemStackTo(inSlot, MemoryBankContainer.PAGE_SIZE, slots.size(), true))
                return ItemStack.EMPTY;
        } else {
            if (!(inSlot.getItem() instanceof SnapshotItem)) return ItemStack.EMPTY;
            if (!moveItemStackTo(inSlot, 0, MemoryBankContainer.PAGE_SIZE, false))
                return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        container.setChanged();
        super.removed(player);
    }
}