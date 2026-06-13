package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.block.ProjectorAcceptance;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.registry.BeyondMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ProjectorMenu extends AbstractContainerMenu {
    private static final int PROJECTOR_SLOTS = ProjectorBlockEntity.SLOTS;
    private static final int DATA_COUNT = 5;

    private final Container container;
    private final ContainerData data;
    private final BlockPos blockPos;

    public ProjectorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    private ProjectorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, resolveClientContainer(pos), resolveClientData(pos), pos);
    }

    private static Container resolveClientContainer(BlockPos pos) {
        BlockEntity be = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getBlockEntity(pos) : null;
        return be instanceof ProjectorBlockEntity projector ? projector : new SimpleContainer(PROJECTOR_SLOTS);
    }

    private static ContainerData resolveClientData(BlockPos pos) {
        BlockEntity be = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getBlockEntity(pos) : null;
        if (be instanceof ProjectorBlockEntity projector) {
            return new ContainerData() {
                @Override
                public int get(int i) {
                    return switch (i) {
                        case 0 -> projector.getMode();
                        case 1 -> projector.getCarouselIndex();
                        case 2 -> projector.isCarouselAuto() ? 1 : 0;
                        case 3 -> projector.getCarouselPeriod();
                        case 4 -> projector.isFlipped() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int i, int v) {
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        }
        return new SimpleContainerData(DATA_COUNT);
    }

    public ProjectorMenu(int containerId, Inventory playerInventory, Container container, ContainerData data, BlockPos pos) {
        super(BeyondMenus.PROJECTOR.get(), containerId);
        this.blockPos = pos;
        this.container = container;
        this.data = data != null ? data : new SimpleContainerData(DATA_COUNT);
        checkContainerSize(this.container, PROJECTOR_SLOTS);
        checkContainerDataCount(this.data, DATA_COUNT);

        for (int i = 0; i < PROJECTOR_SLOTS; i++) {
            int col = i % 2;
            int row = i / 2;
            this.addSlot(new Slot(this.container, i, 86 + col * 18, 20 + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ProjectorAcceptance.accepts(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        this.addDataSlots(this.data);

        for (int k = 0; k < 3; ++k) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + k * 9 + 9, 22 + l * 18, 118 + k * 18));
            }
        }

        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 22 + i1 * 18, 176));
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
            int invStart = PROJECTOR_SLOTS;
            int invEnd = PROJECTOR_SLOTS + 36;
            if (index < PROJECTOR_SLOTS) {
                if (!this.moveItemStackTo(moving, invStart, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Pre-check acceptance to bail before moveItemStackTo's own mayPlace gate.
                if (!ProjectorAcceptance.accepts(moving) || !this.moveItemStackTo(moving, 0, PROJECTOR_SLOTS, false)) {
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

    public int getMode() {
        return this.data.get(0);
    }

    public int getCarouselIndex() {
        return this.data.get(1);
    }

    public boolean isCarouselAuto() {
        return this.data.get(2) != 0;
    }

    public int getCarouselPeriod() {
        return this.data.get(3);
    }

    public boolean isFlipped() {
        return this.data.get(4) != 0;
    }

    public ResourceLocation getGradeId() {
        return container instanceof ProjectorBlockEntity p ? p.getGradeId() : Grades.AS_PHOTO;
    }
}
