package com.thebeyond.data.assets;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
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

        // Use the block's own registry key, NOT block.asItem().toString(). For blocks registered
        // without a BlockItem (registerBlockWithoutItem), asItem() falls back to Items.AIR, which
        // makes getLangName produce "Air" — that's why gellid_void_block, void_flame, ectoplasm,
        // etc. were all showing as "Air" in tooltips and Jade.
        // Gellid Void Block is a liquid — display name should be just "Gellid Void", not "Gellid Void Block".
        blocks.forEach(block -> {
            String key = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (key.equals("the_beyond:gellid_void_block")) {
                add(block, "Gellid Void");
            } else {
                add(block, getLangName(key));
            }
        });

        Set<MobEffect> effects = BuiltInRegistries.MOB_EFFECT.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.MOB_EFFECT.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        effects.forEach(effect -> add(effect, getName(effect.getDescriptionId())));

        Set<EntityType<?>> mobs = BuiltInRegistries.ENTITY_TYPE.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        mobs.forEach(mob -> {
            String path = BuiltInRegistries.ENTITY_TYPE.getKey(mob).getPath();
            // "Totem Of Respite" → "Totem of Respite" — prepositions should stay lowercase.
            if (path.equals("totem_of_respite")) {
                add(mob, "Totem of Respite");
            } else {
                add(mob, getName(mob.getDescriptionId()));
            }
        });

        // Entity translations added manually as a safety net — DeferredRegister entities
        // may not be populated in BuiltInRegistries at datagen time depending on load order.
        // If the loop above already added them, these will be silently skipped (duplicate key).
        safeAdd("entity.the_beyond.lantern", "Lantern");
        safeAdd("entity.the_beyond.abyssal_nomad", "Abyssal Nomad");
        safeAdd("entity.the_beyond.totem_of_respite", "Totem of Respite");
        safeAdd("entity.the_beyond.gravistar", "Gravistar");
        safeAdd("entity.the_beyond.enderglop", "Enderglop");
        safeAdd("entity.the_beyond.enadrake", "Enadrake");
        safeAdd("entity.the_beyond.enatious_totem", "Enatious Totem");
        safeAdd("entity.the_beyond.knockback_seed", "Knockback Seed");
        safeAdd("entity.the_beyond.poison_seed", "Poison Seed");
        safeAdd("entity.the_beyond.unstable_seed", "Unstable Seed");
        safeAdd("entity.the_beyond.rising_block", "Rising Block");

        // Fluid type translation — shown by Jade and other overlay mods for fluid tooltips.
        safeAdd("fluid_type.the_beyond.gellid_void", "Gellid Void");

        // Biomes are datapack-registered (JSON in data/the_beyond/worldgen/biome/), so they
        // don't appear in BuiltInRegistries.BIOME at datagen time and can't be iterated like
        // the registries above. Add them by hand. Without these keys, mods that surface
        // biome names (Xaero's, EMI, /locate output, F3) show the raw resource path.
        add("biome.the_beyond.attracta_expanse", "Attracta Expanse");
        add("biome.the_beyond.pearlescent_planes", "Pearlescent Planes");
        add("biome.the_beyond.peer_lands", "Peer Lands");
        add("biome.the_beyond.the_paths", "The Paths");
        add("biome.the_beyond.true_void", "True Void");

        add("itemGroup.the_beyond", "The Beyond");

        // Advancements
        //add("advancements.the_beyond.root.title", "The Beyond");
        //add("advancements.the_beyond.root.description", "");

        add("advancements.the_beyond.befriend_lantern.title", "Equivalent Exchange");
        add("advancements.the_beyond.befriend_lantern.description", "Gain a lantern's trust using a soul torch");

        add("advancements.the_beyond.brush_lantern.title", "Spirit and Away");
        add("advancements.the_beyond.brush_lantern.description", "Get close enough to a lantern to brush it");

        add("advancements.the_beyond.ectoplasmic_ignition.title", "Let There Be Light");
        add("advancements.the_beyond.ectoplasmic_ignition.description", "Use ectoplasm on a lit bonfire to create a live flame");

        add("advancements.the_beyond.ectoplasmic_ignition_2.title", "Speedrun");
        add("advancements.the_beyond.ectoplasmic_ignition_2.description", "Use ectoplasm on a purple fire bonfire to create a livid flame");

        add("advancements.the_beyond.pass_the_torch.title", "Pass the Torch");
        add("advancements.the_beyond.pass_the_torch.description", "Carry a live flame to an unlit bonfire and light it");

        add("advancements.the_beyond.offering_remembered.title", "An Offering Remembered");
        add("advancements.the_beyond.offering_remembered.description", "Give a remembrance to an abyssal nomad");

        add("advancements.the_beyond.sacred_passage.title", "Sacred Passage");
        add("advancements.the_beyond.sacred_passage.description", "Mount a sitting nomad and trust the journey");

        add("advancements.the_beyond.memories_returned.title", "Memories Returned");
        add("advancements.the_beyond.memories_returned.description", "Offer 5 remembrances to a fountain");

        add("advancements.the_beyond.defying_the_void.title", "Defying the Void");
        add("advancements.the_beyond.defying_the_void.description", "Obtain a Totem of Respite - hold it when you die to keep your items");

        add("advancements.the_beyond.so_below.title", "So Below");
        add("advancements.the_beyond.so_below.description", "Walk on the void river with Pathfinder Boots");

        add("advancements.the_beyond.as_above.title", "As Above");
        add("advancements.the_beyond.as_above.description", "Soar through a migration storm using your elytra");

        add("advancements.the_beyond.gift_enadrake.title", "Building Blocks");
        add("advancements.the_beyond.gift_enadrake.description", "Gift an enadrake an item");

        add("advancements.the_beyond.gift_rare_enadrake.title", "Wealth and Equality");
        add("advancements.the_beyond.gift_rare_enadrake.description", "Gift an enadrake an item of epic rarity");

        add("advancements.the_beyond.complete_refuge.title", "Growth and Infrastructure");
        add("advancements.the_beyond.complete_refuge.description", "Place a refuge near an enadrake village and let them activate it for you");

        add("advancements.the_beyond.full_power_magnet.title", "Slingshot");
        add("advancements.the_beyond.full_power_magnet.description", "Use a magnet to pull yourself somewhere 32 blocks away");
    }

    /**
     * Like {@link #add(String, String)} but silently skips the key if it was already
     * registered by the automatic registry loops above. Avoids the
     * {@code IllegalStateException("Duplicate translation key")} that LanguageProvider
     * throws on repeated keys.
     */
    private final Set<String> addedKeys = new java.util.HashSet<>();

    @Override
    public void add(String key, String value) {
        addedKeys.add(key);
        super.add(key, value);
    }

    private void safeAdd(String key, String value) {
        if (!addedKeys.contains(key)) {
            add(key, value);
        }
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
    public String getName(String id) {
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
