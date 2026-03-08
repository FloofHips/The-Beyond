package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondMenus;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class RefugeMenu extends AbstractContainerMenu {
    private final Container refuge;
    private final RefugeMenu.PaymentSlot paymentSlot;
    private final ContainerLevelAccess access;
    private final ContainerData refugeData;


    public RefugeMenu(int containerId, Container container) {
        this(containerId, container, new SimpleContainerData(3), ContainerLevelAccess.NULL);
    }

    public RefugeMenu(int containerId, Container container, ContainerData refugeData, ContainerLevelAccess access) {
        super(BeyondMenus.REFUGE.get(), containerId);
        this.refuge = new SimpleContainer(1) {
            public boolean canPlaceItem(int p_39066_, ItemStack p_39067_) {
                return p_39067_.is(ItemTags.BEACON_PAYMENT_ITEMS);
            }

            public int getMaxStackSize() {
                return 1;
            }
        };
        checkContainerDataCount(refugeData, 0);
        this.refugeData = refugeData;
        this.access = access;
        this.paymentSlot = new RefugeMenu.PaymentSlot(this.refuge, 0, 40, 30);
        this.addSlot(this.paymentSlot);
        this.addDataSlots(refugeData);
        int i = 36;
        int j = 137;

        for(int k = 0; k < 3; ++k) {
            for(int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(container, l + k * 9 + 9, 8 + l * 18, 94 + k * 18));
            }
        }

        for(int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(container, i1, 8 + i1 * 18, 152));
        }

    }

    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            ItemStack itemstack = this.paymentSlot.remove(this.paymentSlot.getMaxStackSize());
            if (!itemstack.isEmpty()) {
                player.drop(itemstack, false);
            }
        }

    }

    public boolean stillValid(Player player) {
        return stillValid(this.access, player, BeyondBlocks.REFUGE.get());
    }

    public void setData(int id, int data) {
        super.setData(id, data);
        this.broadcastChanges();
    }

    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot)this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index == 0) {
                if (!this.moveItemStackTo(itemstack1, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else {
                if (this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }

                if (index >= 1 && index < 28) {
                    if (!this.moveItemStackTo(itemstack1, 28, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 28 && index < 37) {
                    if (!this.moveItemStackTo(itemstack1, 1, 28, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 1, 37, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }

    public boolean hasPayment() {
        return !this.refuge.getItem(0).isEmpty();
    }

    class PaymentSlot extends Slot {
        public PaymentSlot(Container container, int containerIndex, int xPosition, int yPosition) {
            super(container, containerIndex, xPosition, yPosition);
        }

        public boolean mayPlace(ItemStack stack) {
            return stack.is(ItemTags.BEACON_PAYMENT_ITEMS);
        }

        public int getMaxStackSize() {
            return 1;
        }
    }
}
