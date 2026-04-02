package com.thebeyond.data;

import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class BeyondAdvancements extends AdvancementProvider {
    public BeyondAdvancements(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, existingFileHelper, List.of(new BeyondAdvancementGenerator()));
    }

    public static class BeyondAdvancementGenerator implements AdvancementProvider.AdvancementGenerator {

        @Override
        public void generate(HolderLookup.Provider provider, Consumer<AdvancementHolder> consumer, ExistingFileHelper existingFileHelper) {

            // Root advancement - tab icon only, granted immediately
            //AdvancementHolder root = Advancement.Builder.advancement()
            //        .display(
            //                new ItemStack(BeyondItems.VOID_CRYSTAL.get()),
            //                Component.translatable("advancements.the_beyond.root.title"),
            //                Component.translatable("advancements.the_beyond.root.description"),
            //                ResourceLocation.withDefaultNamespace("textures/block/end_stone.png"),
            //                AdvancementType.TASK,
            //                false, false, true
            //        )
            //        .addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
            //        .save(consumer, "the_beyond:the_beyond/root");

            AdvancementHolder root = new AdvancementHolder(
                     ResourceLocation.parse("minecraft:end/enter_end_gateway"),
                    null
            );

            // === BONFIRE BRANCH ===

            // Befriend Lantern
            AdvancementHolder lanternTrust = Advancement.Builder.advancement()
                    .parent(root)
                    .display(
                            new ItemStack(Items.SOUL_TORCH),
                            Component.translatable("advancements.the_beyond.befriend_lantern.title"),
                            Component.translatable("advancements.the_beyond.befriend_lantern.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("befriend_lantern",
                            BeyondCriteriaTriggers.BEFRIEND_LANTERN.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/befriend_lantern");

            // Brush Lantern
            AdvancementHolder lanternBrush = Advancement.Builder.advancement()
                    .parent(lanternTrust)
                    .display(
                            new ItemStack(Items.BRUSH),
                            Component.translatable("advancements.the_beyond.brush_lantern.title"),
                            Component.translatable("advancements.the_beyond.brush_lantern.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("brush_lantern",
                            BeyondCriteriaTriggers.BRUSH_LANTERN.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/brush_lantern");

            // Ectoplasmic Ignition - use ectoplasm on a lit bonfire to get a live flame
            AdvancementHolder ectoplasmicIgnition = Advancement.Builder.advancement()
                    .parent(lanternTrust)
                    .display(
                            new ItemStack(BeyondItems.LIVE_FLAME.get()),
                            Component.translatable("advancements.the_beyond.ectoplasmic_ignition.title"),
                            Component.translatable("advancements.the_beyond.ectoplasmic_ignition.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("obtain_live_flame",
                            BeyondCriteriaTriggers.OBTAIN_LIVE_FLAME.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/ectoplasmic_ignition");

            AdvancementHolder ectoplasmicIgnition2 = Advancement.Builder.advancement()
                    .parent(ectoplasmicIgnition)
                    .display(
                            new ItemStack(BeyondItems.LIVID_FLAME.get()),
                            Component.translatable("advancements.the_beyond.ectoplasmic_ignition_2.title"),
                            Component.translatable("advancements.the_beyond.ectoplasmic_ignition_2.description"),
                            null,
                            AdvancementType.CHALLENGE,
                            true, true, true
                    )
                    .addCriterion("obtain_livid_flame",
                            BeyondCriteriaTriggers.OBTAIN_LIVID_FLAME.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/ectoplasmic_ignition_2");


            // Pass the Torch - light an unlit bonfire with a live flame
            AdvancementHolder passTheTorch = Advancement.Builder.advancement()
                    .parent(ectoplasmicIgnition)
                    .display(
                            new ItemStack(BeyondItems.LIVID_FLAME.get()),
                            Component.translatable("advancements.the_beyond.pass_the_torch.title"),
                            Component.translatable("advancements.the_beyond.pass_the_torch.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("light_bonfire",
                            BeyondCriteriaTriggers.LIGHT_BONFIRE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/pass_the_torch");

            // === NOMAD BRANCH ===

            // An Offering Remembered - give a remembrance to a nomad
            AdvancementHolder offeringRemembered = Advancement.Builder.advancement()
                    .parent(root)
                    .display(
                            new ItemStack(BeyondItems.REMEMBRANCE_RING.get()),
                            Component.translatable("advancements.the_beyond.offering_remembered.title"),
                            Component.translatable("advancements.the_beyond.offering_remembered.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("give_remembrance",
                            BeyondCriteriaTriggers.GIVE_REMEMBRANCE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/offering_remembered");

            // Sacred Passage - ride a nomad
            AdvancementHolder sacredPassage = Advancement.Builder.advancement()
                    .parent(offeringRemembered)
                    .display(
                            new ItemStack(BeyondItems.ABYSSAL_SHROUD.get()),
                            Component.translatable("advancements.the_beyond.sacred_passage.title"),
                            Component.translatable("advancements.the_beyond.sacred_passage.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("ride_nomad",
                            BeyondCriteriaTriggers.RIDE_NOMAD.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/sacred_passage");

            // Memories Returned - drop a remembrance into a fountain
            AdvancementHolder memoriesReturned = Advancement.Builder.advancement()
                    .parent(sacredPassage)
                    .display(
                            new ItemStack(BeyondItems.REMEMBRANCE_MEMORY.get()),
                            Component.translatable("advancements.the_beyond.memories_returned.title"),
                            Component.translatable("advancements.the_beyond.memories_returned.description"),
                            null,
                            AdvancementType.GOAL,
                            true, true, false
                    )
                    .addCriterion("fountain_offering",
                            BeyondCriteriaTriggers.FOUNTAIN_OFFERING.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/memories_returned");

            // === TOTEM ===

            // Defying the Void - obtain a totem of respite
            AdvancementHolder defyingTheVoid = Advancement.Builder.advancement()
                    .parent(passTheTorch)
                    .display(
                            new ItemStack(BeyondItems.TOTEM_OF_RESPITE.get()),
                            Component.translatable("advancements.the_beyond.defying_the_void.title"),
                            Component.translatable("advancements.the_beyond.defying_the_void.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("use_totem",
                            BeyondCriteriaTriggers.USE_TOTEM.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/defying_the_void");

            // === EXPLORATION ===

            // So Below - walk on the void river with pathfinder boots
            AdvancementHolder soBelow = Advancement.Builder.advancement()
                    .parent(memoriesReturned)
                    .display(
                            new ItemStack(BeyondItems.PATHFINDER_BOOTS.get()),
                            Component.translatable("advancements.the_beyond.so_below.title"),
                            Component.translatable("advancements.the_beyond.so_below.description"),
                            null,
                            AdvancementType.CHALLENGE,
                            true, true, false
                    )
                    .addCriterion("walk_void_river",
                            BeyondCriteriaTriggers.WALK_VOID_RIVER.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/so_below");

            // As Above - ride a lantern during a thunderstorm
            AdvancementHolder asAbove = Advancement.Builder.advancement()
                    .parent(soBelow)
                    .display(
                            new ItemStack(BeyondItems.LANTERN_SHED.get()),
                            Component.translatable("advancements.the_beyond.as_above.title"),
                            Component.translatable("advancements.the_beyond.as_above.description"),
                            null,
                            AdvancementType.CHALLENGE,
                            true, true, true
                    )
                    .addCriterion("ride_lantern_thunder",
                            BeyondCriteriaTriggers.MIGRATION_STORM.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/as_above");

            // === ENADRAKES ===
            AdvancementHolder giftEnadrake = Advancement.Builder.advancement()
                    .parent(root)
                    .display(
                            new ItemStack(Items.STICK),
                            Component.translatable("advancements.the_beyond.gift_enadrake.title"),
                            Component.translatable("advancements.the_beyond.gift_enadrake.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("gift_enadrake",
                            BeyondCriteriaTriggers.GIFT_ENADRAKE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/gift_enadrake");

//            AdvancementHolder giftRareEnadrake = Advancement.Builder.advancement()
//                    .parent(giftEnadrake)
//                    .display(
//                            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
//                            Component.translatable("advancements.the_beyond.gift_rare_enadrake.title"),
//                            Component.translatable("advancements.the_beyond.gift_rare_enadrake.description"),
//                            null,
//                            AdvancementType.TASK,
//                            true, true, false
//                    )
//                    .addCriterion("gift_rare_enadrake",
//                            BeyondCriteriaTriggers.GIFT_RARE_ENADRAKE.get().createCriterion(
//                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
//                    .save(consumer, "the_beyond:the_beyond/gift_rare_enadrake");

            AdvancementHolder completeRefuge = Advancement.Builder.advancement()
                    .parent(giftEnadrake)
                    .display(
                            new ItemStack(BeyondBlocks.REFUGE.asItem()),
                            Component.translatable("advancements.the_beyond.complete_refuge.title"),
                            Component.translatable("advancements.the_beyond.complete_refuge.description"),
                            null,
                            AdvancementType.CHALLENGE,
                            true, true, false
                    )
                    .addCriterion("complete_refuge",
                            BeyondCriteriaTriggers.COMPLETE_REFUGE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/complete_refuge");

            // === MAGNET ===
            AdvancementHolder fullPowerMagnet = Advancement.Builder.advancement()
                    .parent(root)
                    .display(
                            new ItemStack(BeyondItems.MAGNET.get()),
                            Component.translatable("advancements.the_beyond.full_power_magnet.title"),
                            Component.translatable("advancements.the_beyond.full_power_magnet.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("full_power_magnet",
                            BeyondCriteriaTriggers.FULL_POWER_MAGNET.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/full_power_magnet");

        }
    }
}
