package com.thebeyond.data;

import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
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
                            true, false, false
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
                            true, false, false
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

            // nomad branch

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
                            true, false, false
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
                            true, false, false
                    )
                    .addCriterion("fountain_offering",
                            BeyondCriteriaTriggers.FOUNTAIN_OFFERING.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/memories_returned");

            // totem

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

            // exploration

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
                    .addCriterion("walk_auroracite",
                            BeyondCriteriaTriggers.WALK_AURORACITE.get().createCriterion(
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

            AdvancementHolder giftRareEnadrake = Advancement.Builder.advancement()
                    .parent(giftEnadrake)
                    .display(
                            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                            Component.translatable("advancements.the_beyond.gift_rare_enadrake.title"),
                            Component.translatable("advancements.the_beyond.gift_rare_enadrake.description"),
                            null,
                            AdvancementType.TASK,
                            true, false, false
                    )
                    .addCriterion("gift_rare_enadrake",
                            BeyondCriteriaTriggers.GIFT_RARE_ENADRAKE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/gift_rare_enadrake");

            AdvancementHolder completeRefuge = Advancement.Builder.advancement()
                    .parent(giftRareEnadrake)
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

            AdvancementHolder use_refuge = Advancement.Builder.advancement()
                    .parent(completeRefuge)
                    .display(
                            new ItemStack(Items.IRON_INGOT.asItem()),
                            Component.translatable("advancements.the_beyond.use_refuge.title"),
                            Component.translatable("advancements.the_beyond.use_refuge.description"),
                            null,
                            AdvancementType.GOAL,
                            true, true, false
                    )
                    .addCriterion("use_refuge",
                            BeyondCriteriaTriggers.USE_REFUGE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/use_refuge");

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

            // === FUMAROLE UPLANDS ===
            AdvancementHolder geyser = Advancement.Builder.advancement()
                    .parent(root)
                    .display(
                            new ItemStack(BeyondBlocks.GAUSS_VENT.get()),
                            Component.translatable("advancements.the_beyond.geyser.title"),
                            Component.translatable("advancements.the_beyond.geyser.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("geyser",
                            BeyondCriteriaTriggers.GEYSER.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/geyser");

            AdvancementHolder casting = Advancement.Builder.advancement()
                    .parent(geyser)
                    .display(
                            new ItemStack(BeyondItems.BRITTLE_PICKAXE.get()),
                            Component.translatable("advancements.the_beyond.casting.title"),
                            Component.translatable("advancements.the_beyond.casting.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("casting",
                            BeyondCriteriaTriggers.CASTING.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/casting");

            AdvancementHolder freeze = Advancement.Builder.advancement()
                    .parent(casting)
                    .display(
                            new ItemStack(BeyondBlocks.MOLTEN_METAL.get()),
                            Component.translatable("advancements.the_beyond.freeze.title"),
                            Component.translatable("advancements.the_beyond.freeze.description"),
                            null,
                            AdvancementType.TASK,
                            true, false, false
                    )
                    .addCriterion("freeze",
                            BeyondCriteriaTriggers.FREEZE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/freeze");

//            AdvancementHolder brittle_rain = Advancement.Builder.advancement()
//                    .parent(casting)
//                    .display(
//                            new ItemStack(BeyondBlocks.BRITTLE_METAL_BLOCK.get()),
//                            Component.translatable("advancements.the_beyond.brittle_rain.title"),
//                            Component.translatable("advancements.the_beyond.brittle_rain.description"),
//                            null,
//                            AdvancementType.TASK,
//                            true, false, false
//                    )
//                    .addCriterion("brittle_rain",
//                            BeyondCriteriaTriggers.BRITTLE_RAIN.get().createCriterion(
//                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
//                    .save(consumer, "the_beyond:the_beyond/brittle_rain");


            AdvancementHolder snapshot = Advancement.Builder.advancement()
                    .parent(freeze)
                    .display(
                            new ItemStack(BeyondItems.SNAPSHOT.get()),
                            Component.translatable("advancements.the_beyond.snapshot.title"),
                            Component.translatable("advancements.the_beyond.snapshot.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("snapshot",
                            BeyondCriteriaTriggers.SNAPSHOT.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/snapshot");

            AdvancementHolder memory_full = Advancement.Builder.advancement()
                    .parent(snapshot)
                    .display(
                            new ItemStack(BeyondItems.MEMORY_BANK.get()),
                            Component.translatable("advancements.the_beyond.memory_full.title"),
                            Component.translatable("advancements.the_beyond.memory_full.description"),
                            null,
                            AdvancementType.TASK,
                            true, false, false
                    )
                    .addCriterion("memory_full",
                            BeyondCriteriaTriggers.MEMORY_FULL.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/memory_full");

            AdvancementHolder fill_projector = Advancement.Builder.advancement()
                    .parent(snapshot)
                    .display(
                            new ItemStack(BeyondBlocks.PROJECTOR.get()),
                            Component.translatable("advancements.the_beyond.fill_projector.title"),
                            Component.translatable("advancements.the_beyond.fill_projector.description"),
                            null,
                            AdvancementType.TASK,
                            true, false, false
                    )
                    .addCriterion("fill_projector",
                            BeyondCriteriaTriggers.FILL_PROJECTOR.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/fill_projector");

            AdvancementHolder discover_projection = Advancement.Builder.advancement()
                    .parent(fill_projector)
                    .display(
                            new ItemStack(BeyondItems.REMEMBRANCE_BEADS.get()),
                            Component.translatable("advancements.the_beyond.discover_projection.title"),
                            Component.translatable("advancements.the_beyond.discover_projection.description"),
                            null,
                            AdvancementType.GOAL,
                            true, true, true
                    )
                    .addCriterion("discover_projection",
                            BeyondCriteriaTriggers.DISCOVER_PROJECTION.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/discover_projection");

            AdvancementHolder discover_all_projection = Advancement.Builder.advancement()
                                .parent(discover_projection)
                                .display(
                                        new ItemStack(BeyondItems.REMEMBRANCE_CLOTH.get()),
                                        Component.translatable("advancements.the_beyond.discover_all_projection.title"),
                                        Component.translatable("advancements.the_beyond.discover_all_projection.description"),
                                        null,
                                        AdvancementType.CHALLENGE,
                                        true, true, true
                                )
                                .addCriterion("discover_all_projection_0",
                                        BeyondCriteriaTriggers.DISCOVER_ALL_PROJECTION_0.get().createCriterion(
                                                new PlayerTrigger.TriggerInstance(Optional.empty())))
                                .addCriterion("discover_all_projection_1",
                                        BeyondCriteriaTriggers.DISCOVER_ALL_PROJECTION_1.get().createCriterion(
                                                new PlayerTrigger.TriggerInstance(Optional.empty())))
                                .addCriterion("discover_all_projection_2",
                                        BeyondCriteriaTriggers.DISCOVER_ALL_PROJECTION_2.get().createCriterion(
                                                new PlayerTrigger.TriggerInstance(Optional.empty())))
                                .addCriterion("discover_all_projection_3",
                                        BeyondCriteriaTriggers.DISCOVER_ALL_PROJECTION_3.get().createCriterion(
                                                new PlayerTrigger.TriggerInstance(Optional.empty())))
                                .save(consumer, "the_beyond:the_beyond/discover_all_projection");

            // === CHESTRAL HOLLOWS ===
            AdvancementHolder encounter_stalker = Advancement.Builder.advancement()
                    .parent(root)
                    .display(
                            new ItemStack(BeyondItems.STALKER_SEGMENT.get()),
                            Component.translatable("advancements.the_beyond.encounter_stalker.title"),
                            Component.translatable("advancements.the_beyond.encounter_stalker.description"),
                            null,
                            AdvancementType.TASK,
                            true, false, false
                    )
                    .addCriterion("encounter_stalker",
                            BeyondCriteriaTriggers.ENCOUNTER_STALKER.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/encounter_stalker");

            AdvancementHolder walk_the_rope = Advancement.Builder.advancement()
                    .parent(encounter_stalker)
                    .display(
                            new ItemStack(BeyondItems.COILED_STALK.get()),
                            Component.translatable("advancements.the_beyond.walk_the_rope.title"),
                            Component.translatable("advancements.the_beyond.walk_the_rope.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("walk_the_rope",
                            BeyondCriteriaTriggers.WALKING_THE_ROPE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/walk_the_rope");


            // === LUSTROUS ECHOES ===
            AdvancementHolder collect_pearl = Advancement.Builder.advancement()
                    .parent(root)
                    .display(
                            new ItemStack(BeyondBlocks.BEDAZZLED_END_STONE.get()),
                            Component.translatable("advancements.the_beyond.collect_pearl.title"),
                            Component.translatable("advancements.the_beyond.collect_pearl.description"),
                            null,
                            AdvancementType.TASK,
                            true, false, false
                    )
                    .addCriterion("collect_pearl",
                            BeyondCriteriaTriggers.COLLECT_PEARL.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/collect_pearl");

            AdvancementHolder strip_mirror = Advancement.Builder.advancement()
                    .parent(collect_pearl)
                    .display(
                            new ItemStack(BeyondBlocks.MIRROR.get()),
                            Component.translatable("advancements.the_beyond.strip_mirror.title"),
                            Component.translatable("advancements.the_beyond.strip_mirror.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("strip_mirror",
                            BeyondCriteriaTriggers.STRIP_MIRROR.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/strip_mirror");

            AdvancementHolder tame_bauble = Advancement.Builder.advancement()
                    .parent(collect_pearl)
                    .display(
                            new ItemStack(Items.CYAN_DYE),
                            Component.translatable("advancements.the_beyond.tame_bauble.title"),
                            Component.translatable("advancements.the_beyond.tame_bauble.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("tame_bauble",
                            BeyondCriteriaTriggers.TAME_BAUBLE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/tame_bauble");

            AdvancementHolder bucket_trinket = Advancement.Builder.advancement()
                    .parent(tame_bauble)
                    .display(
                            new ItemStack(BeyondItems.TRINKET_BUCKET.get()),
                            Component.translatable("advancements.the_beyond.bucket_trinket.title"),
                            Component.translatable("advancements.the_beyond.bucket_trinket.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("bucket_trinket",
                            BeyondCriteriaTriggers.BUCKET_TRINKET.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/bucket_trinket");

            AdvancementHolder select_trinket = Advancement.Builder.advancement()
                    .parent(bucket_trinket)
                    .display(
                            new ItemStack(BeyondItems.OCARINA.get()),
                            Component.translatable("advancements.the_beyond.select_trinket.title"),
                            Component.translatable("advancements.the_beyond.select_trinket.description"),
                            null,
                            AdvancementType.GOAL,
                            true, true, false
                    )
                    .addCriterion("select_trinket",
                            BeyondCriteriaTriggers.SELECT_TRINKET.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/select_trinket");

            AdvancementHolder wax_trinket = Advancement.Builder.advancement()
                    .parent(tame_bauble)
                    .display(
                            new ItemStack(Items.HONEYCOMB),
                            Component.translatable("advancements.the_beyond.wax_trinket.title"),
                            Component.translatable("advancements.the_beyond.wax_trinket.description"),
                            null,
                            AdvancementType.TASK,
                            true, true, false
                    )
                    .addCriterion("wax_trinket",
                            BeyondCriteriaTriggers.WAX_TRINKET.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/wax_trinket");

            AdvancementHolder rake_nacre = Advancement.Builder.advancement()
                    .parent(collect_pearl)
                    .display(
                            new ItemStack(Items.DIAMOND_HOE),
                            Component.translatable("advancements.the_beyond.rake_nacre.title"),
                            Component.translatable("advancements.the_beyond.rake_nacre.description"),
                            null,
                            AdvancementType.TASK,
                            true, false, false
                    )
                    .addCriterion("rake_nacre",
                            BeyondCriteriaTriggers.RAKE_NACRE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/rake_nacre");

            AdvancementHolder clinamen = Advancement.Builder.advancement()
                    .parent(collect_pearl)
                    .display(
                            new ItemStack(BeyondItems.PEARL_BEAD.get()),
                            Component.translatable("advancements.the_beyond.clinamen.title"),
                            Component.translatable("advancements.the_beyond.clinamen.description"),
                            null,
                            AdvancementType.GOAL,
                            true, true, false
                    )
                    .addCriterion("clinamen",
                            BeyondCriteriaTriggers.CLINAMEN.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/clinamen");

            AdvancementHolder create_bauble = Advancement.Builder.advancement()
                    .parent(clinamen)
                    .display(
                            new ItemStack(BeyondItems.BAUBLE_SPAWN_EGG.get()),
                            Component.translatable("advancements.the_beyond.create_bauble.title"),
                            Component.translatable("advancements.the_beyond.create_bauble.description"),
                            null,
                            AdvancementType.GOAL,
                            true, true, false
                    )
                    .addCriterion("create_bauble",
                            BeyondCriteriaTriggers.CREATE_BAUBLE.get().createCriterion(
                                    new PlayerTrigger.TriggerInstance(Optional.empty())))
                    .save(consumer, "the_beyond:the_beyond/create_bauble");


        }
    }
}
