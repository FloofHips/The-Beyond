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

        // Advancements
        add("advancements.the_beyond.root.title", "The Beyond");
        add("advancements.the_beyond.root.description", "");

        add("advancements.the_beyond.ectoplasmic_ignition.title", "Ectoplasmic Ignition");
        add("advancements.the_beyond.ectoplasmic_ignition.description", "Use ectoplasm on a lit bonfire to create a live flame");

        add("advancements.the_beyond.pass_the_torch.title", "Pass the Torch");
        add("advancements.the_beyond.pass_the_torch.description", "Carry a live flame to an unlit bonfire and light it");

        add("advancements.the_beyond.offering_remembered.title", "An Offering Remembered");
        add("advancements.the_beyond.offering_remembered.description", "Give a remembrance to an abyssal nomad");

        add("advancements.the_beyond.sacred_passage.title", "Sacred Passage");
        add("advancements.the_beyond.sacred_passage.description", "Mount a sitting nomad and ride to the beyond");

        add("advancements.the_beyond.memories_returned.title", "Memories Returned");
        add("advancements.the_beyond.memories_returned.description", "Offer a remembrance to a void sea fountain");

        add("advancements.the_beyond.defying_the_void.title", "Defying the Void");
        add("advancements.the_beyond.defying_the_void.description", "Obtain a Totem of Respite - hold it when you die to keep your items");

        add("advancements.the_beyond.so_below.title", "So Below");
        add("advancements.the_beyond.so_below.description", "Walk on the void river with Pathfinder Boots");

        add("advancements.the_beyond.as_above.title", "As Above");
        add("advancements.the_beyond.as_above.description", "Ride a lantern during a thunderstorm");
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
