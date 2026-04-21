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
 * JEI integration for progressive-discovery hiding. Runs each ItemStack through
 * {@link HiddenContentFilter#isHidden} and removes hidden stacks at runtime. Tracks
 * removed stacks in {@link #removedByUs} so add-back only affects entries this plugin
 * hid — stacks hidden by other mods/configs are left alone.
 *
 * <p>Refresh is invoked via {@link JeiCompatBridge#refresh()}. Not loaded when JEI is
 * absent: {@code @JeiPlugin} is service-loaded by JEI itself.
 */
@JeiPlugin
public class BeyondJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("the_beyond", "jei_plugin");

    /** Stacks removed by this plugin — re-added as-is once unlocked by a new knowledge key. */
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
            // Feature off — re-add everything previously hidden by this plugin.
            if (!removedByUs.isEmpty()) {
                ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(removedByUs));
                removedByUs.clear();
            }
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // Compute the currently-visible stacks that should now be hidden.
        // getAllItemStacks does not include stacks already removed at runtime, so
        // this list is already the delta of *newly* hidden entries.
        List<ItemStack> shouldBeHidden = new ArrayList<>();
        for (ItemStack stack : ingredientManager.getAllItemStacks()) {
            if (HiddenContentFilter.isHidden(stack, player)) {
                shouldBeHidden.add(stack);
            }
        }

        // Previously-hidden stacks that are no longer hidden (progression advanced)
        // need to be re-added.
        List<ItemStack> toAddBack = new ArrayList<>();
        for (ItemStack prev : removedByUs) {
            if (!HiddenContentFilter.isHidden(prev, player)) {
                toAddBack.add(prev);
            }
        }


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
