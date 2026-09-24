package com.thebeyond.client.menu;

import com.thebeyond.common.item.MemoryBankItem;
import com.thebeyond.common.item.SnapshotItem;
import com.thebeyond.common.network.MemoryBankPagePacket;
import com.thebeyond.common.registry.BeyondMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class MemoryBankMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 24;

    public final ItemStack stack;
    private int page = 0;
    private NonNullList<ItemStack> cache;
    public boolean magnifyMode = false;
    public ItemStack focusedItem = null;
    public int focusedSlot = -1;

    public MemoryBankMenu(int id, Inventory inv, ItemStack stack) {
        super(BeyondMenus.MEMORY_BANK.get(), id);
        this.stack = stack;
        refreshCache();

        for (int i = 0; i < 2; i++) {
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 3; col++) {

                    int idx = i * 12 + row * 3 + col;
                    int x = -68 + col * 40 + i * 128;
                    int y = 9 + row * 40;

                    addSlot(new BankSlot(idx, x, y));
                }
            }
        }

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(inv, 9 + col + row * 3, 187 + col * 18, 8 + row * 18));
            }
        }

        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(inv, i, 245, 8 + i * 18));
        }
    }

    public int getPage() { return page; }

    public int getMaxPage() { return highestUsed() / PAGE_SIZE; }

    public void changePage(int delta, Player player) {
        int target = page + delta;
        if (target < 0 || target > 4) return;
        page = target;
        refreshCache();
        if (player instanceof ServerPlayer sp)
            PacketDistributor.sendToPlayer(sp, new MemoryBankPagePacket(containerId, target));
        broadcastChanges();
    }

    public void setPage(int p) {
        this.page = p;
        refreshCache();
    }

    private void refreshCache() {
        cache = MemoryBankItem.slots(stack);
    }

    private int highestUsed() {
        NonNullList<ItemStack> all = MemoryBankItem.slots(stack);
        for (int i = all.size() - 1; i >= 0; i--)
            if (!all.get(i).isEmpty()) return i + 1;
        return 0;
    }

    private void saveCache() {
        MemoryBankItem.writeBack(stack, cache);
    }

    private class BankSlot extends Slot {
        private final int bankIndex;

        BankSlot(int bankIndex, int x, int y) {
            super(new SimpleContainer(PAGE_SIZE), bankIndex, x, y);
            this.bankIndex = bankIndex;
        }

        private int real() { return page * PAGE_SIZE + bankIndex; }

        @Override
        public ItemStack getItem() {
            int r = real();
            return r < cache.size() ? cache.get(r) : ItemStack.EMPTY;
        }

        @Override
        public void set(ItemStack stack) {
            int r = real();
            cache.set(r, stack);
            saveCache();
            setChanged();
        }

        @Override
        public void setChanged() {
            super.setChanged();
            saveCache();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack current = getItem();
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack split = current.split(amount);
            set(current);
            return split;
        }

        @Override
        public boolean mayPlace(ItemStack s) {
            return s.getItem() instanceof SnapshotItem;
        }

        @Override
        public boolean mayPickup(Player player) {
            return true;
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (this.magnifyMode) {
            if (slotId >= 0 && slotId < 24) {
                focusedSlot = slotId;
                focusedItem = slots.get(slotId).getItem().copy();
            }
            return;
        }

        super.clicked(slotId, button, type, player);
        refreshCache();
    }

    public ItemStack getMagnifiedStack() { return focusedItem; }
    public int getMagnifiedSlot() { return focusedSlot; }

    public void clearMagnify() {
        focusedItem = ItemStack.EMPTY;
        focusedSlot = -1;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        if (this.magnifyMode) return ItemStack.EMPTY;
        Slot slot = slots.get(i);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack inSlot = slot.getItem();
        ItemStack copy = inSlot.copy();

        if (i < PAGE_SIZE) {
            if (!moveItemStackTo(inSlot, PAGE_SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!(inSlot.getItem() instanceof SnapshotItem)) return ItemStack.EMPTY;
            if (!moveItemStackTo(inSlot, 0, PAGE_SIZE, false)) return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getInventory().contains(stack);
    }
}