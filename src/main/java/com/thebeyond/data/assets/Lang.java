package com.thebeyond.data.assets;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Lang extends LanguageProvider {
    public Lang(PackOutput output) {
        super(output, TheBeyond.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        Set<Item> items = BuiltInRegistries.ITEM.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.ITEM.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        takeAll(items, item -> item instanceof BlockItem);

        items.forEach(item -> add(item, getLangName(item.toString())));

        Set<Block> blocks = BuiltInRegistries.BLOCK.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.BLOCK.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        blocks.forEach(block -> add(block, getLangName(block.asItem().toString())));

        Set<MobEffect> effects = BuiltInRegistries.MOB_EFFECT.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.MOB_EFFECT.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        effects.forEach(effect -> add(effect, getEffectName(effect.getDescriptionId())));


        add("itemGroup.the_beyond", "The Beyond");
    }

    public String getLangName(String id) {
        String[] words = id.toString().split(":")[1].split("_");

        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return result.toString().trim();
    }
    public String getEffectName(String id) {
        System.out.println(id);
        String[] words = id.toString().split("\\.")[2].split("_");

        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return result.toString().trim();
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
