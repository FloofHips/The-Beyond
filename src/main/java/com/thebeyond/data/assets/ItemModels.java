package com.thebeyond.data.assets;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ItemModels extends ItemModelProvider {
    public static final String GENERATED = "item/generated";

    public ItemModels(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, TheBeyond.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        Set<Item> items = BuiltInRegistries.ITEM.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.ITEM.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        items.remove(BeyondBlocks.POLAR_PILLAR.asItem());
        items.remove(BeyondBlocks.POLAR_ANTENNA.asItem());
        items.remove(BeyondBlocks.POLAR_BULB.asItem());
        items.remove(BeyondBlocks.MAGNOLILLY.asItem());

        items.remove(BeyondBlocks.VOID_CRYSTAL.asItem());
        items.remove(BeyondBlocks.VOID_FLAME.asItem());
        items.remove(BeyondBlocks.STARDUST.asItem());
        items.remove(BeyondBlocks.CREEPING_ZYMOTE.asItem());
        items.remove(BeyondBlocks.REACHING_ZYMOTE.asItem());

        itemGeneratedModel(BeyondBlocks.VOID_CRYSTAL.asItem(), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"item/void_crystal"));
        itemGeneratedModel(BeyondBlocks.VOID_FLAME.asItem(), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"item/void_flame"));
        itemGeneratedModel(BeyondBlocks.CREEPING_ZYMOTE.asItem(), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/creeping_zymote"));
        itemGeneratedModel(BeyondBlocks.REACHING_ZYMOTE.asItem(), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/reaching_zymote"));

        takeAll(items, i -> i instanceof BlockItem).forEach(item -> blockBasedModel(item, ""));
        takeAll(items, i -> i instanceof SpawnEggItem).forEach(this::spawnEggGeneratedModel);

        items.forEach(item -> itemGeneratedModel(item, resourceItem(itemName(item))));
    }
    public void spawnEggGeneratedModel(Item item) {
        withExistingParent(itemName(item), "item/template_spawn_egg");
    }

    public void itemGeneratedModel(Item item, ResourceLocation texture) {
        withExistingParent(itemName(item), GENERATED).texture("layer0", texture);
    }

    public void blockBasedModel(Item item, String suffix) {
        withExistingParent(itemName(item), resourceBlock(itemName(item) + suffix));
    }

    private String itemName(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    public ResourceLocation resourceItem(String path) {
        return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "item/" + path);
    }

    public ResourceLocation resourceBlock(String path) {
        return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "block/" + path);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Collection<T> takeAll(Set<? extends T> src, T... items) {
        List<T> ret = Arrays.asList(items);
        for (T item : items) {
            if (!src.contains(item)) {
                TheBeyond.LOGGER.warn("Item {} not found in set", item);
            }
        }
        if (!src.removeAll(ret)) {
            TheBeyond.LOGGER.warn("takeAll array didn't yield anything ({})", Arrays.toString(items));
        }
        return ret;
    }

    public static <T> Collection<T> takeAll(Set<T> src, Predicate<T> pred) {
        List<T> ret = new ArrayList<>();

        Iterator<T> iter = src.iterator();
        while (iter.hasNext()) {
            T item = iter.next();
            if (pred.test(item)) {
                iter.remove();
                ret.add(item);
            }
        }

        if (ret.isEmpty()) {
            TheBeyond.LOGGER.warn("takeAll predicate yielded nothing", new Throwable());
        }
        return ret;
    }
}
