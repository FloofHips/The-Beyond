package com.thebeyond.compat.jei;

import com.thebeyond.common.awareness.BeyondAwareness;
import com.thebeyond.common.awareness.HiddenContentFilter;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hides locked items and recipes in JEI, and remembers what it hid so it can put them back once unlocked. */
@JeiPlugin
public class BeyondJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("the_beyond", "jei_plugin");

    /** Items we hid, so we can add them back once unlocked. */
    private final List<ItemStack> removedByUs = new ArrayList<>();

    private IIngredientManager ingredientManager;
    private IRecipeManager recipeManager;

    /** Recipes we hid, grouped by type so we can put them back. */
    private final Map<RecipeType<?>, Set<Object>> hiddenByType = new HashMap<>();

    /** What the player knew last time we ran, so we can skip the sweep if nothing changed. */
    private Set<ResourceLocation> lastKnown;

    /** The few recipes that touch locked items - the only ones that can ever be hidden. Built lazily. */
    private List<IndexedRecipe> gatedRecipes;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        this.ingredientManager = runtime.getIngredientManager();
        this.recipeManager = runtime.getRecipeManager();
        JeiCompatBridge.setRefreshHook(this::refresh);
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        this.ingredientManager = null;
        this.recipeManager = null;
        this.removedByUs.clear();
        this.hiddenByType.clear();
        this.lastKnown = null;
        this.gatedRecipes = null;
        JeiCompatBridge.setRefreshHook(null);
    }

    /** Re-checks what should be hidden and only applies the difference. */
    private void refresh() {
        if (ingredientManager == null) return;
        if (!BeyondAwareness.gateEnabled()) {
            // Feature off — re-add everything previously hidden by this plugin.
            if (!removedByUs.isEmpty()) {
                ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(removedByUs));
                removedByUs.clear();
            }
            unhideAllRecipes();
            lastKnown = null;
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // Grab the known set once so we're not re-reading it for every item below.
        Set<ResourceLocation> known = BeyondAwareness.knownSnapshot(player);
        if (known.equals(lastKnown)) return;   // nothing changed → skip the pass
        lastKnown = new HashSet<>(known);

        // Already-removed stacks aren't in this list, so it's just the newly-hidden ones.
        List<ItemStack> shouldBeHidden = new ArrayList<>();
        for (ItemStack stack : ingredientManager.getAllItemStacks()) {
            if (HiddenContentFilter.isHidden(stack, known)) {
                shouldBeHidden.add(stack);
            }
        }

        // Things we hid before that are now unlocked go back in.
        List<ItemStack> toAddBack = new ArrayList<>();
        for (ItemStack prev : removedByUs) {
            if (!HiddenContentFilter.isHidden(prev, known)) {
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

        refreshRecipes(known);
    }

    /** First pass finds the recipes that use locked items; after that we only re-check those, so big packs stay cheap. */
    private void refreshRecipes(Set<ResourceLocation> known) {
        if (recipeManager == null) return;
        if (gatedRecipes == null) {
            gatedRecipes = new ArrayList<>();
            Set<ResourceLocation> gated = HiddenContentFilter.gateableItemIds();
            recipeManager.createRecipeCategoryLookup().includeHidden().get()
                    .forEach(category -> buildCategory(category, gated, known));
        } else {
            Map<IRecipeCategory<?>, List<Object>> byCategory = new HashMap<>();
            for (IndexedRecipe ir : gatedRecipes) {
                byCategory.computeIfAbsent(ir.category(), k -> new ArrayList<>()).add(ir.recipe());
            }
            byCategory.forEach((category, recipes) -> reevalCategory(category, recipes, known));
        }
    }

    /** Indexes a category's locked-item recipes and sets their starting hidden state. */
    private <T> void buildCategory(IRecipeCategory<T> category, Set<ResourceLocation> gated, Set<ResourceLocation> known) {
        RecipeType<T> type = category.getRecipeType();
        Set<Object> tracked = hiddenByType.computeIfAbsent(type, k -> new HashSet<>());
        List<T> toHide = new ArrayList<>();
        List<T> toUnhide = new ArrayList<>();
        // includeHidden() so recipes we already hid still show up here.
        recipeManager.createRecipeLookup(type).includeHidden().get().forEach(recipe -> {
            boolean touches;
            try { touches = touchesGated(category, recipe, gated); }
            catch (Throwable t) { touches = false; }
            if (!touches) return;   // can never be hidden → skip it for good
            gatedRecipes.add(new IndexedRecipe(category, recipe));
            evaluate(category, recipe, known, tracked, toHide, toUnhide);
        });
        apply(type, tracked, toHide, toUnhide);
    }

    /** Re-checks only the already-indexed recipes of one category. */
    @SuppressWarnings("unchecked")
    private <T> void reevalCategory(IRecipeCategory<T> category, List<Object> recipes, Set<ResourceLocation> known) {
        RecipeType<T> type = category.getRecipeType();
        Set<Object> tracked = hiddenByType.computeIfAbsent(type, k -> new HashSet<>());
        List<T> toHide = new ArrayList<>();
        List<T> toUnhide = new ArrayList<>();
        for (Object o : recipes) {
            evaluate(category, (T) o, known, tracked, toHide, toUnhide);
        }
        apply(type, tracked, toHide, toUnhide);
    }

    private <T> void evaluate(IRecipeCategory<T> category, T recipe, Set<ResourceLocation> known,
                              Set<Object> tracked, List<T> toHide, List<T> toUnhide) {
        boolean shouldHide;
        try { shouldHide = recipeShouldHide(category, recipe, known); }
        catch (Throwable t) { shouldHide = false; }   // if we can't tell, leave it visible
        boolean wasHidden = tracked.contains(recipe);
        if (shouldHide && !wasHidden) toHide.add(recipe);
        else if (!shouldHide && wasHidden) toUnhide.add(recipe);
    }

    private <T> void apply(RecipeType<T> type, Set<Object> tracked, List<T> toHide, List<T> toUnhide) {
        if (!toHide.isEmpty()) { recipeManager.hideRecipes(type, toHide); tracked.addAll(toHide); }
        if (!toUnhide.isEmpty()) { recipeManager.unhideRecipes(type, toUnhide); tracked.removeAll(toUnhide); }
    }

    /** Does this recipe use a locked item anywhere it could matter? Checks the same spots recipeShouldHide does. */
    private <T> boolean touchesGated(IRecipeCategory<T> category, T recipe, Set<ResourceLocation> gated) {
        Recipe<?> vanilla = asRecipe(recipe);
        if (vanilla != null) {
            for (Ingredient slot : vanilla.getIngredients()) {
                for (ItemStack alt : slot.getItems()) {
                    if (gated.contains(BuiltInRegistries.ITEM.getKey(alt.getItem()))) return true;
                }
            }
            return false;
        }
        for (ITypedIngredient<?> out : recipeManager.getRecipeIngredients(category, recipe).getIngredients(RecipeIngredientRole.OUTPUT)) {
            if (out.getItemStack().map(s -> gated.contains(BuiltInRegistries.ITEM.getKey(s.getItem()))).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** Hide a recipe when one of its input slots has no unlocked option, falling back to the output for ones we can't read. */
    private <T> boolean recipeShouldHide(IRecipeCategory<T> category, T recipe,
                                         Set<ResourceLocation> known) {
        Recipe<?> vanilla = asRecipe(recipe);
        if (vanilla != null) {
            for (Ingredient slot : vanilla.getIngredients()) {
                if (slot.isEmpty()) continue;   // empty slot, nothing required
                boolean satisfiable = false;
                for (ItemStack alt : slot.getItems()) {
                    if (!HiddenContentFilter.isHidden(alt, known)) { satisfiable = true; break; }
                }
                if (!satisfiable) return true;
            }
            return false;   // every slot has an unlocked option → visible
        }
        // Can't see the inputs here, so just go by the output.
        IIngredientSupplier ingredients = recipeManager.getRecipeIngredients(category, recipe);
        for (ITypedIngredient<?> out : ingredients.getIngredients(RecipeIngredientRole.OUTPUT)) {
            if (out.getItemStack().map(s -> HiddenContentFilter.isHidden(s, known)).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** Digs a vanilla Recipe out of whatever JEI handed us, or null if it isn't one. */
    private static Recipe<?> asRecipe(Object recipe) {
        if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof Recipe<?> r) return r;
        if (recipe instanceof Recipe<?> r) return r;
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void unhideAllRecipes() {
        if (recipeManager != null) {
            for (Map.Entry<RecipeType<?>, Set<Object>> entry : hiddenByType.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    recipeManager.unhideRecipes((RecipeType) entry.getKey(), entry.getValue());
                }
            }
        }
        hiddenByType.clear();
    }

    /** A locked-item recipe paired with the category we need to re-check and hide it. */
    private record IndexedRecipe(IRecipeCategory<?> category, Object recipe) {}
}
