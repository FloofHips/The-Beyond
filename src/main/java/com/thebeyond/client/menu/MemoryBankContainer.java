package com.thebeyond.client.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class MemoryBankContainer implements Container {

    protected NonNullList<ItemStack> items;
    public static final int PAGE_SIZE = 24;
    public static final int DATA_PAGE = 0;
    private int page = 0;
    protected ItemStack bank;

    public MemoryBankContainer(ItemStack bank) {
        this.bank = bank;
        this.reload();
    }

    public int getPage() { return page; }
    public void setPage(int p) { this.page = p; }

    public int highestUsed() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) return i + 1;
        }
        return 0;
    }

    @Override
    public int getContainerSize() { return PAGE_SIZE; }

    private int real(int index) { return (page * PAGE_SIZE) + index; }

    @Override
    public ItemStack getItem(int index) {
        int i = real(index);
        return i < items.size() ? items.get(i) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack s = ContainerHelper.removeItem(items, real(index), count);
        setChanged();
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack s = ContainerHelper.takeItem(items, real(index));
        setChanged();
        return s;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        int i = real(index);
        //while (items.size() <= i) //items.add(ItemStack.EMPTY);
        items.set(i, stack);
        setChanged();
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    public int getMaxPage() {
        return highestUsed() / PAGE_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public void setChanged() {
        if (bank.isEmpty()) return;
        int used = highestUsed();
        NonNullList<ItemStack> trimmed = NonNullList.withSize(used, ItemStack.EMPTY);
        for (int i = 0; i < used; i++) trimmed.set(i, items.get(i));
        bank.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(trimmed));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getInventory().contains(bank);
    }

    public void reload() {
        this.items = NonNullList.withSize(120, ItemStack.EMPTY);
        ItemContainerContents contents = bank.get(DataComponents.CONTAINER);
        if (contents != null) contents.copyInto(items);
    }
}
