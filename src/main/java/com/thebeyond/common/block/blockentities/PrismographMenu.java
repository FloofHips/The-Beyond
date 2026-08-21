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
public class PrismographMenu extends AbstractContainerMenu {
    private static final int PRISMOGRAPH_SLOTS = PrismographBlockEntity.SLOTS;

    private final Container container;
    private final BlockPos blockPos;

    // Client side: IMenuTypeExtension hands back the buf written at open time.
    public PrismographMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    private PrismographMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, resolveClientContainer(pos), pos);
    }

    private static Container resolveClientContainer(BlockPos pos) {
        BlockEntity be = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getBlockEntity(pos) : null;
        return be instanceof PrismographBlockEntity prismograph ? prismograph : new SimpleContainer(PRISMOGRAPH_SLOTS);
    }

    public PrismographMenu(int containerId, Inventory playerInventory, Container container, BlockPos pos) {
        super(BeyondMenus.PRISMOGRAPH.get(), containerId);
        this.blockPos = pos;
        this.container = container;
        checkContainerSize(this.container, PRISMOGRAPH_SLOTS);

        this.addSlot(new Slot(this.container, PrismographBlockEntity.FILM, 71, 7) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(BeyondTags.PRISMOGRAPH_FILM);
            }

            @Override
            public int getMaxStackSize() {
                return PrismographBlockEntity.MAX_FILM;
            }
        });

        // Fuel tag is empty for now, so this slot stays inert.
        this.addSlot(new Slot(this.container, PrismographBlockEntity.FUEL, 89, 7) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(BeyondTags.PRISMOGRAPH_FUEL);
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
            int invStart = PRISMOGRAPH_SLOTS;
            int invEnd = PRISMOGRAPH_SLOTS + 36;          // past the hotbar
            if (index < PRISMOGRAPH_SLOTS) {
                if (!this.moveItemStackTo(moving, invStart, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (moving.is(BeyondTags.PRISMOGRAPH_FILM)) {
                    if (!this.moveItemStackTo(moving, PrismographBlockEntity.FILM, PrismographBlockEntity.FILM + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (moving.is(BeyondTags.PRISMOGRAPH_FUEL)) {
                    if (!this.moveItemStackTo(moving, PrismographBlockEntity.FUEL, PrismographBlockEntity.FUEL + 1, false)) {
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
