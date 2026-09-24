package com.thebeyond.common.item;

import com.thebeyond.client.menu.MemoryBankMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

public class MemoryBankItem extends Item {
    public static final int CAPACITY = 120;

    public MemoryBankItem(Properties properties) {
        super(properties);
    }

    public static NonNullList<ItemStack> slots(ItemStack stack) {
        NonNullList<ItemStack> s = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(s);
        return s;
    }

    public static void writeBack(ItemStack stack, NonNullList<ItemStack> s) {
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(s));
    }

    private static int firstEmpty(NonNullList<ItemStack> s) {
        for (int i = 0; i < s.size(); i++) {
            if (s.get(i).isEmpty()) return i;
        }
        return -1;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (player.containerMenu instanceof MemoryBankMenu menu && menu.stack == stack) return false;
        if (action != ClickAction.SECONDARY) return false;

        ItemStack onSlot = slot.getItem();
        if (onSlot.isEmpty() || !(onSlot.getItem() instanceof SnapshotItem)) return false;

        NonNullList<ItemStack> s = slots(stack);
        int idx = firstEmpty(s);
        if (idx == -1) return false;

        ItemStack taken = slot.safeTake(1, 1, player);
        if (taken.isEmpty()) return false;

        s.set(idx, taken);
        writeBack(stack, s);
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (player.containerMenu instanceof MemoryBankMenu menu && menu.stack == stack) return false;
        if (action != ClickAction.SECONDARY) return false;
        if (!slot.allowModification(player)) return false;
        if (other.isEmpty() || !(other.getItem() instanceof SnapshotItem)) return false;

        NonNullList<ItemStack> s = slots(stack);
        int idx = firstEmpty(s);
        if (idx == -1) return false;

        s.set(idx, other.copyWithCount(1));
        other.shrink(1);
        access.set(other);
        writeBack(stack, s);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (player.isShiftKeyDown()) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(level.random.nextInt(48665565), true));
            return InteractionResultHolder.success(stack);
        }

        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new MemoryBankMenu(id, inv, stack),
                    stack.has(DataComponents.CUSTOM_NAME) ? stack.get(DataComponents.CUSTOM_NAME) : this.getName(stack)
            ), buf -> ItemStack.STREAM_CODEC.encode(buf, stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.literal(String.valueOf(slots(stack).stream().count())).withStyle(ChatFormatting.BLUE));
    }
}
