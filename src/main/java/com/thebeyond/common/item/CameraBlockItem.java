package com.thebeyond.common.item;

import com.thebeyond.common.network.CameraShootPayload;
import com.thebeyond.client.camera.CameraAim;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

import static com.thebeyond.common.block.blockentities.CameraSlots.*;
import net.neoforged.neoforge.network.PacketDistributor;

public class CameraBlockItem extends BlockItem {
    private static final int FILM_BAR_COLOR = 0xF0E0C0;

    public CameraBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    private static NonNullList<ItemStack> slots(ItemStack camera) {
        NonNullList<ItemStack> s = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        camera.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(s);
        return s;
    }

    private static void saveSlots(ItemStack camera, NonNullList<ItemStack> s) {
        camera.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(s));
    }

    public static int filmCount(ItemStack camera) {
        return slots(camera).get(FILM).getCount();
    }

    public static boolean hasFilm(ItemStack camera) {
        return filmCount(camera) > 0;
    }

    public static void consumeFilm(ItemStack camera) {
        NonNullList<ItemStack> s = slots(camera);
        if (!s.get(FILM).isEmpty()) {
            s.get(FILM).shrink(1);
            saveSlots(camera, s);
        }
    }

    // Dormant until #thebeyond:camera_fuel is populated and these + the shoot gate are uncommented.
    // public static int fuelCount(ItemStack camera) {
    //     return slots(camera).get(FUEL).getCount();
    // }
    // public static boolean hasFuel(ItemStack camera) {
    //     return fuelCount(camera) > 0;
    // }
    // private static void consumeFuel(ItemStack camera) {
    //     NonNullList<ItemStack> s = slots(camera);
    //     if (!s.get(FUEL).isEmpty()) {
    //         s.get(FUEL).shrink(1);
    //         saveSlots(camera, s);
    //     }
    // }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            return InteractionResult.PASS; // PASS routes to use() for the handheld photo, skipping placement
        }
        return super.useOn(context);
    }

    // Film gate, consume, and capture are server-authoritative in the CameraShootPayload handler.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasFilm(stack)) {
            if (level.isClientSide) {
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 0.6f);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            if (!CameraAim.isAiming()) {
                CameraAim.set(true);
            } else {
                CameraAim.clear();
                PacketDistributor.sendToServer(new CameraShootPayload(hand));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * filmCount(stack) / MAX_FILM);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return FILM_BAR_COLOR;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        NonNullList<ItemStack> s = slots(stack);
        return Optional.of(new CameraTooltip(s.get(FILM), s.get(FUEL)));
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack camera, ItemStack carried, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        NonNullList<ItemStack> s = slots(camera);
        if (carried.isEmpty()) {
            ItemStack out = removeOne(s);
            if (out.isEmpty()) {
                return false;
            }
            saveSlots(camera, s);
            access.set(out);
            player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8f, 1.0f);
            return true;
        }
        int target = slotFor(carried);
        if (target < 0) {
            return false;
        }
        if (insertFrom(s, target, carried)) {
            saveSlots(camera, s);
            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.8f, 1.0f);
        }
        return true; // consume the click for any film/fuel cursor, even if the slot was full
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack camera, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        NonNullList<ItemStack> s = slots(camera);
        ItemStack onSlot = slot.getItem();
        if (onSlot.isEmpty()) {
            ItemStack out = removeOne(s);
            if (out.isEmpty()) {
                return false;
            }
            ItemStack leftover = slot.safeInsert(out);
            if (!leftover.isEmpty()) {
                putBack(s, leftover);
            }
            saveSlots(camera, s);
            player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8f, 1.0f);
            return true;
        }
        int target = slotFor(onSlot);
        if (target < 0) {
            return false;
        }
        int max = target == FILM ? MAX_FILM : onSlot.getMaxStackSize();
        int cap = max - s.get(target).getCount();
        if (cap > 0) {
            ItemStack taken = slot.safeTake(onSlot.getCount(), cap, player);
            if (insertFrom(s, target, taken)) {
                saveSlots(camera, s);
                player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.8f, 1.0f);
            }
            if (!taken.isEmpty()) {
                slot.safeInsert(taken); // defensive: took exactly the capacity, normally a no-op
            }
        }
        return true;
    }

    private static ItemStack removeOne(NonNullList<ItemStack> s) {
        for (int i : new int[]{FILM, FUEL}) {
            if (!s.get(i).isEmpty()) {
                ItemStack out = s.get(i);
                s.set(i, ItemStack.EMPTY);
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    // Film slot is capped at MAX_FILM, not the stack's max size.
    private static boolean insertFrom(NonNullList<ItemStack> s, int target, ItemStack src) {
        if (src.isEmpty()) {
            return false;
        }
        int max = target == FILM ? MAX_FILM : src.getMaxStackSize();
        ItemStack held = s.get(target);
        int cap = max - held.getCount();
        if (cap <= 0) {
            return false;
        }
        if (held.isEmpty()) {
            s.set(target, src.split(Math.min(cap, src.getCount())));
            return true;
        }
        if (ItemStack.isSameItemSameComponents(held, src)) {
            int move = Math.min(cap, src.getCount());
            held.grow(move);
            src.shrink(move);
            return move > 0;
        }
        return false;
    }

    private static void putBack(NonNullList<ItemStack> s, ItemStack leftover) {
        int t = slotFor(leftover);
        if (t >= 0 && s.get(t).isEmpty()) {
            s.set(t, leftover);
        }
    }
}
