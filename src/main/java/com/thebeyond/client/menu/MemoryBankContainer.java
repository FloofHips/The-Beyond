package com.thebeyond.client.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class MemoryBankContainer implements Container {

    protected NonNullList<ItemStack> items;
    protected ItemStack bank;

    public MemoryBankContainer(ItemStack bank) {
        this.bank = bank;
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);

        ItemContainerContents contents = bank.get(DataComponents.CONTAINER);

        if (contents != null) {
            contents.copyInto(items);
        }
    }

    @Override
    public int getContainerSize() {
        return 120;
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack itemstack = ContainerHelper.removeItem(items, index, count);
        setChanged();
        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        ItemStack stack = ContainerHelper.takeItem(items, i);
        setChanged();
        return stack;
    }

    @Override
    public void setItem(int i, ItemStack itemStack) {
        items.set(i, itemStack);
        setChanged();
    }

    @Override
    public void setChanged() {
        bank.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getInventory().contains(bank);
    }
}
