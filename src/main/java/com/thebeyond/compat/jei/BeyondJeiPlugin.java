package com.thebeyond.compat.jei;

import com.thebeyond.common.knowledge.BeyondKnowledge;
import com.thebeyond.common.knowledge.HiddenContentFilter;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI integration for progressive-discovery hiding. Runs every known ItemStack through
 * {@link HiddenContentFilter#isHidden} and removes hidden stacks at runtime; tracks what
 * we removed in {@link #removedByUs} so add-back is symmetric (won't re-add items other
 * mods/configs hid for their own reasons).
 *
 * <p>Refresh trigger: {@link JeiCompatBridge#refresh()}. Until the knowledge S2C packet
 * exists, only {@code onRuntimeAvailable} fires. Never loaded when JEI is absent —
 * {@code @JeiPlugin} is service-loaded by JEI itself.
 */
@JeiPlugin
public class BeyondJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("the_beyond", "jei_plugin");

    /** Stacks we removed — re-added as-is when a new knowledge key unlocks them. */
    private final List<ItemStack> removedByUs = new ArrayList<>();

    private IIngredientManager ingredientManager;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        this.ingredientManager = runtime.getIngredientManager();
        JeiCompatBridge.setRefreshHook(this::refresh);
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        this.ingredientManager = null;
        this.removedByUs.clear();
        JeiCompatBridge.setRefreshHook(null);
    }

    /** Diffs the target hidden set against {@link #removedByUs} and applies only the delta. */
    private void refresh() {
        if (ingredientManager == null) return;
        if (!BeyondKnowledge.gateEnabled()) {
            // Feature off — re-add everything we previously hid.
            if (!removedByUs.isEmpty()) {
                ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(removedByUs));
                removedByUs.clear();
            }
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // Recompute desired hidden set from the full catalogue. This
        // includes stacks we already hid — those stay hidden.
        List<ItemStack> shouldBeHidden = new ArrayList<>();
        for (ItemStack stack : ingredientManager.getAllItemStacks()) {
            if (HiddenContentFilter.isHidden(stack, player)) {
                shouldBeHidden.add(stack);
            }
        }

        // Stacks we previously hid that should now be visible (player
        // advanced their knowledge): add them back.
        List<ItemStack> toAddBack = new ArrayList<>();
        for (ItemStack prev : removedByUs) {
            if (!HiddenContentFilter.isHidden(prev, player)) {
                toAddBack.add(prev);
            }
        }

        // Stacks that should be hidden but aren't tracked yet: remove them.
        // (getAllItemStacks after a previous removal no longer contains
        // already-removed stacks, so shouldBeHidden is already the delta
        // of *newly* hidden entries.)
        if (!shouldBeHidden.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, shouldBeHidden);
            removedByUs.addAll(shouldBeHidden);
        }

        if (!toAddBack.isEmpty()) {
            ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toAddBack);
            removedByUs.removeAll(toAddBack);
        }
    }
}
