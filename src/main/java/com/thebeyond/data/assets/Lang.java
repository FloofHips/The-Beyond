package com.thebeyond.data.assets;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.sounds.SoundEvent;
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

        items.remove(BeyondItems.TRINKET_BUCKET.asItem());
        items.forEach(item -> add(item, getLangName(item.toString())));

        Set<Block> blocks = BuiltInRegistries.BLOCK.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.BLOCK.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        // the block's own key, since blocks without an item would read as Air through asItem()
        blocks.forEach(block -> {
            String key = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (key.equals("the_beyond:gellid_void_block")) {
                add(block, "Gellid Void");
            } else if (key.equals("the_beyond:camera")) {
                add(block, "Pinhole Camera");
            } else {
                add(block, getLangName(key));
            }
        });

        Set<MobEffect> effects = BuiltInRegistries.MOB_EFFECT.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.MOB_EFFECT.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        effects.forEach(effect -> add(effect, getName(effect.getDescriptionId())));

        // the deafening potion names live in vanilla's potion key namespace
        add("item.minecraft.potion.effect.deafening", "Potion of Deafening");
        add("item.minecraft.splash_potion.effect.deafening", "Splash Potion of Deafening");
        add("item.minecraft.lingering_potion.effect.deafening", "Lingering Potion of Deafening");
        add("item.minecraft.tipped_arrow.effect.deafening", "Arrow of Deafening");
        add("item.the_beyond.trinket_bucket", "Bucket of Trinket");
        add("tooltip.the_beyond.deafening_drinkable", "Does nothing when drunk");

        Set<EntityType<?>> mobs = BuiltInRegistries.ENTITY_TYPE.stream().filter(i -> TheBeyond.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(i).getNamespace()))
                .collect(Collectors.toSet());

        mobs.forEach(mob -> {
            String path = BuiltInRegistries.ENTITY_TYPE.getKey(mob).getPath();
            // the preposition stays lowercase
            if (path.equals("totem_of_respite")) {
                add(mob, "Totem of Respite");
            } else {
                add(mob, getName(mob.getDescriptionId()));
            }
        });

        // a safety net for entities missing from the registry at datagen, duplicates are skipped
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
        safeAdd("entity.the_beyond.brubble", "Brubble");
        safeAdd("entity.the_beyond.bauble", "Bauble");
        safeAdd("entity.the_beyond.sibling", "Sibling");
        safeAdd("entity.the_beyond.trinket", "Trinket");
        safeAdd("entity.the_beyond.smoke_fuse", "Smoke Fuse");
        safeAdd("entity.the_beyond.coiled_stalk", "Coiled Stalk");

        safeAdd("fluid_type.the_beyond.gellid_void", "Gellid Void");

        // datapack biomes are not in the registry at datagen, so their names are added by hand

        add("biome.the_beyond.attracta_expanse", "Attracta Expanse");
        add("biome.the_beyond.pearlescent_planes", "Pearlescent Planes");
        add("biome.the_beyond.peer_lands", "Peer Lands");
        add("biome.the_beyond.the_paths", "The Paths");
        add("biome.the_beyond.true_void", "True Void");
        add("biome.the_beyond.lustrous_echoes", "Lustrous Echoes");
        add("biome.the_beyond.fumarole_uplands", "Fumarole Uplands");
        add("biome.the_beyond.chestral_hollows", "Chestral Hollows");

        add("itemGroup.the_beyond", "The Beyond");

        // GUI titles + projector/refuge button labels (block/item names are generated from the registry above)
        add("container.the_beyond.camera", "Camera");
        add("container.the_beyond.projector", "Projector");
        add("screen.the_beyond.projector.rotate", "Rotate");
        add("screen.the_beyond.projector.flip", "Flip");
        add("screen.the_beyond.projector.missing", "Missing a light source");
        add("screen.the_beyond.refuge.block", "Block %s");
        add("screen.the_beyond.prismograph.first_person", "Switch to first person to use");
        add("screen.the_beyond.prismograph.no_film", "Out of film");
        add("screen.the_beyond.memory_bank.next", "Next page");
        add("screen.the_beyond.memory_bank.previous", "Previous page");
        add("screen.the_beyond.memory_bank.magnify", "Magnify");
        add("screen.the_beyond.ocarina.trinkets_selected", "Selected %s Trinkets");
        add("screen.the_beyond.ocarina.trinkets_deselected", "Deselected %s Trinkets");
        add("screen.the_beyond.ocarina.no_trinkets", "No Trinkets selected");

        add("item.the_beyond.ocarina.mode.select", "Select mode");
        add("item.the_beyond.ocarina.mode.guide", "Guide mode");
        add("item.the_beyond.ocarina.mode.follow", "Follow mode");
        add("item.the_beyond.ocarina.mode.scatter", "Scatter mode");

        add("tooltip.block.the_beyond.brittle_metal.title", "Brittle Metal Casting");
        add("tooltip.block.the_beyond.brittle_metal.desc", "Create tool shape then interact with center block to cast");

        add("trinket.small", "Small");
        add("trinket.medium", "Medium");
        add("trinket.large", "Large");
        add("trinket.growth_color", "Dyed Color: %s");

        add("inventory.the_beyond.category.building", "Building");
        add("inventory.the_beyond.category.functional", "Functional");
        add("inventory.the_beyond.category.equipment", "Equipment");
        add("inventory.the_beyond.category.ingredients", "Ingredients");
        add("inventory.the_beyond.category.mobs", "Mobs");
        add("inventory.the_beyond.category.artifacts", "Artifacts");

        add("inventory.the_beyond.category.the_end", "The End");
        add("inventory.the_beyond.category.attracta_expanse", "Attracta Expanse");
        add("inventory.the_beyond.category.peer_lands", "Peer Lands");
        add("inventory.the_beyond.category.the_paths", "The Paths");
        add("inventory.the_beyond.category.lustrous_echoes", "Lustrous Echoes");
        add("inventory.the_beyond.category.fumarole_uplands", "Fumarole Uplands");
        add("inventory.the_beyond.category.chestral_hollows", "Chestral Hollows");

        // Sounds
        add("subtitles.block.void_crystal.shatter", "Crystal shatters");

        add("subtitles.entity.enderglop.death", "Enderglop dies");
        add("subtitles.entity.enderglop.death_small", "Enderdrop dies");
        add("subtitles.entity.enderglop.hurt", "Enderglop hurts");
        add("subtitles.entity.enderglop.hurt_small", "Enderdrop hurts");
        add("subtitles.entity.enderglop.squish", "Enderglop squishes");
        add("subtitles.entity.enderglop.squish_small", "Enderdrop squishes");
        add("subtitles.entity.enderglop.armor", "Enderglop armors up");
        add("subtitles.entity.enderglop.armor_hurt", "Enderglop's armor hurts");
        add("subtitles.entity.enderglop.armor_break", "Enderglop's armor shatters");

        add("subtitles.entity.abyssal_nomad.death", "Abyssal Nomad dies");
        add("subtitles.entity.abyssal_nomad.hurt", "Abyssal Nomad hurts");
        add("subtitles.entity.abyssal_nomad.attack", "Abyssal Nomad attacks");
        add("subtitles.entity.abyssal_nomad.danger", "Abyssal Nomad alerts");
        add("subtitles.entity.abyssal_nomad.heal", "Abyssal Nomad remembers");
        add("subtitles.entity.abyssal_nomad.idle", "Abyssal Nomad bellows");
        add("subtitles.entity.abyssal_nomad.nod", "Abyssal Nomad is pleased");
        add("subtitles.entity.abyssal_nomad.remember", "Abyssal Nomad is remembering");
        add("subtitles.entity.abyssal_nomad.tear", "Abyssal Nomad offers");
        add("subtitles.entity.abyssal_nomad.teleport", "Abyssal Nomad teleports");
        add("subtitles.entity.abyssal_nomad.thank", "Abyssal Nomad is thankful");

        add("subtitles.entity.enatious_totem.death", "Enatious Totem dies");
        add("subtitles.entity.enatious_totem.hurt", "Enatious Totem hurts");
        add("subtitles.entity.enatious_totem.leave", "Enatious Totem retreats");
        add("subtitles.entity.enatious_totem.ready", "Enatious Totem readies up");
        add("subtitles.entity.enatious_totem.roots_creaking", "Roots creak ominously");
        add("subtitles.entity.enatious_totem.shockwave", "Enatious Totem pushes");
        add("subtitles.entity.enatious_totem.shoot", "Enatious Totem shoots");
        add("subtitles.entity.enatious_totem.spawn", "Enatious Totem emerges");
        add("subtitles.entity.enatious_totem.teleport", "Enatious Totem teleports");

        add("subtitles.entity.seed.knockback_burst", "Knockback seed bursts");
        add("subtitles.entity.seed.poison_bounce", "Poison seed skips");
        add("subtitles.entity.seed.poison_land", "Poison seed bursts");
        add("subtitles.entity.seed.unstable_burst", "Unstable seed bursts");
        add("subtitles.entity.seed.unstable_fail", "Unstable seed wastes away");
        add("subtitles.entity.seed.unstable_fly", "Unstable seed twirls");

        add("subtitles.block.gellid_void.burst", "Void echoes");

        add("subtitles.entity.enadrake.teleport", "Enadrake teleports");
        add("subtitles.entity.enadrake.death", "Enadrake dies");
        add("subtitles.entity.enadrake.hurt", "Enadrake hurts");
        add("subtitles.entity.enadrake.screech", "Enadrake screeches");

        add("subtitles.entity.stalker.death", "Stalker dies");
        add("subtitles.entity.stalker.hurt", "Stalker hurts");
        add("subtitles.entity.stalker.pop", "Stalker appears");
        add("subtitles.entity.stalker.retreat", "Stalker retreats");
        add("subtitles.entity.stalker.bite", "Stalker bites down");


        add("subtitles.entity.brubble.death", "Brubble goes back");
        add("subtitles.entity.brubble.hurt", "Brubble hurts");
        add("subtitles.entity.brubble.sigh", "Brubble defeated");
        add("subtitles.entity.brubble.destroy", "Brubble's rocket destroyed");
        add("subtitles.entity.brubble.appear", "Brubble appears");
        add("subtitles.entity.brubble.attack", "Brubble boosts");
        add("subtitles.entity.brubble.emily", "Emily greets");
        add("subtitles.entity.brubble.step", "Brubble's rocket clinks");
        add("subtitles.entity.brubble.idle", "Brubble murmurs");
        add("subtitles.entity.brubble.hover", "Brubble hovering");

        add("subtitles.entity.lantern.hurt", "Lantern hurts");
        add("subtitles.entity.lantern.idle", "Wind blows quietly");
        add("subtitles.entity.lantern.teleport", "Lantern teleports");
        add("subtitles.entity.lantern.shed", "Lantern sheds");
        add("subtitles.entity.lantern.spawn", "Lantern has appeared");

        add("subtitles.entity.respite_totem.activate", "Totem of Respite has activated");
        add("subtitles.entity.respite_totem.float", "Totem of Respite floats by");
        add("subtitles.entity.respite_totem.shatter", "Totem of Respite shatters");
        add("subtitles.entity.respite_totem.spawn", "Totem of Respite has appeared");

        add("subtitles.block.bonfire.idle", "Bonfire sings");
        add("subtitles.block.bonfire.idle_corrupted", "Bonfire screams");
        add("subtitles.block.bonfire.ignite", "Item ignited");
        add("subtitles.block.bonfire.search", "Bonfire is searching for twin");
        add("subtitles.block.bonfire.activate", "Bonfire lights up");

        add("block.bonfire.found", "Twin Bonfire found");
        add("block.bonfire.near", "Twin Bonfire nearby");
        add("block.bonfire.none", "This bonfire sits alone");

        add("subtitles.block.pearl_chimes.chime", "Pearls chime");

        add("subtitles.block.polar.emerge", "Enderglop emerges");
        add("subtitles.block.polar.charge", "Polar charge rises up");
        add("subtitles.block.polar.cool", "Polar charge cools down");

        add("subtitles.block.ectoplasm.warn", "Ectoplasm decays");
        add("subtitles.block.ectoplasm.pop", "Ectoplasm pops");

        add("subtitles.block.memor_faucet.open", "Faucet opens");
        add("subtitles.block.memor_faucet.close", "Faucet closes");
        add("subtitles.block.memor_faucet.power", "Faucet powers up");
        add("subtitles.block.memor_faucet.power_final", "Faucet unlocks");
        add("subtitles.block.memor_faucet.absorb", "Faucet accepts offering");

        add("subtitles.block.enadrake_hut.enter", "Enadrake pops in");
        add("subtitles.block.enadrake_hut.leave", "Enadrake pops out");
        add("subtitles.block.enadrake_hut.pop", "Insert item");
        add("subtitles.block.enadrake_hut.spread", "Enadrake hut spreads out");

        add("subtitles.block.refuge.activate", "Refuge protects");
        add("subtitles.block.refuge.branch_place", "Arm placed");
        add("subtitles.block.refuge.roots_spreading", "Roots spread");
        add("subtitles.block.refuge.ready", "Refuge activates");

        add("subtitles.block.pearl.scrape", "Scrape off");
        add("subtitles.block.pearl.impact", "Pearl resonates");
        add("subtitles.block.pearl.clink", "Pearl clinks");
        add("subtitles.entity.sibling.death", "Sibling shatters");
        add("subtitles.entity.sibling.empathy", "Empathy experiences");

        add("subtitles.item.trinket.fill", "Trinket secured");
        add("subtitles.item.trinket.empty", "Bucket empties");

        add("subtitles.entity.pearl.hurt", "Pearl hurts");
        add("subtitles.entity.pearl.death", "Pearl shatters");

        add("subtitles.item.ocarina.play", "Ocarina played");
        add("subtitles.item.ocarina.fail", "Ocarina fails");
        add("subtitles.item.throw", "Item chucked");

        add("subtitles.block.molten_metal.freeze", "Metal freezes rapidly");
        add("subtitles.block.brittle_metal.reform", "Brittle metal reforms");
        add("subtitles.block.brittle_metal.success", "Metal tool casted");
        add("subtitles.block.brittle_metal.fail", "Brittle metal reverberates");
        add("subtitles.block.brittle_metal.shatter", "Brittle metal breaks");
        add("subtitles.block.brittle_metal.impact", "Brittle metal tinks");
        add("subtitles.block.brittle_metal_block.rain", "Rain pitter patters");

        add("subtitles.block.brittle_metal_block.open", "Brittle door opened");
        add("subtitles.block.brittle_metal_block.close", "Brittle door closed");

        add("subtitles.block.gauss_vent.start", "Gauss Erupted");
        add("subtitles.block.gauss_vent.middle", "Gauss venting");
        add("subtitles.block.gauss_vent.end", "Gauss vented");

        add("subtitles.block.projector.idle", "Projector churning");
        add("subtitles.block.projector.switch", "Slide switches");
        add("subtitles.block.projector.activate", "Projector wakes up");
        add("subtitles.block.projector.murmur", "Spectators murmuring");

        add("subtitles.block.nacre.rake", "Player rakes");

        add("subtitles.item.prismograph.close", "Prismograph closed");
        add("subtitles.item.prismograph.open", "Prismograph opened");
        add("subtitles.item.prismograph.insert", "Film inserted");
        add("subtitles.item.prismograph.extract", "Film extracted");
        add("subtitles.item.prismograph.snap", "Snapshot taken");

        add("subtitles.item.magnet.success", "Magnet latches on");
        add("subtitles.item.magnet.fail", "Magnet falls short");

        add("subtitles.item.flame.fail", "Flame extinguishes");

        add("subtitles.item.smoke_fuse.use", "Smoke spreads");

        add("subtitles.item.anchor_leggings.smash_ground", "Anchors down");

        add("advancements.the_beyond.befriend_lantern.title", "Equivalent Exchange");
        add("advancements.the_beyond.befriend_lantern.description", "Gain a Lantern's trust using a Soul Torch");

        add("advancements.the_beyond.brush_lantern.title", "Spirit and Away");
        add("advancements.the_beyond.brush_lantern.description", "Get close enough to a Lantern to brush it");

        add("advancements.the_beyond.ectoplasmic_ignition.title", "Let There Be Light");
        add("advancements.the_beyond.ectoplasmic_ignition.description", "Use Ectoplasm on a lit Bonfire to create a Live Flame");

        add("advancements.the_beyond.ectoplasmic_ignition_2.title", "Speedrun");
        add("advancements.the_beyond.ectoplasmic_ignition_2.description", "Use Ectoplasm on a void fire Bonfire to create a Livid Flame");

        add("advancements.the_beyond.pass_the_torch.title", "Pass the Torch");
        add("advancements.the_beyond.pass_the_torch.description", "Carry a Live Flame to an unlit Bonfire and light it");

        add("advancements.the_beyond.offering_remembered.title", "An Offering Remembered");
        add("advancements.the_beyond.offering_remembered.description", "Give a Remembrance to an Abyssal Nomad");

        add("advancements.the_beyond.sacred_passage.title", "Sacred Passage");
        add("advancements.the_beyond.sacred_passage.description", "Mount a sitting Abyssal Nomad and trust the journey");

        add("advancements.the_beyond.memories_returned.title", "Memories Returned");
        add("advancements.the_beyond.memories_returned.description", "Offer 5 Remembrances to a fountain");

        add("advancements.the_beyond.defying_the_void.title", "Defying the Void");
        add("advancements.the_beyond.defying_the_void.description", "Hold a Totem of Respite on death to keep your items");

        add("advancements.the_beyond.so_below.title", "So Below");
        add("advancements.the_beyond.so_below.description", "Walk on Auroracite using Pathfinder Boots");

        add("advancements.the_beyond.as_above.title", "As Above");
        add("advancements.the_beyond.as_above.description", "Soar through a migration storm");

        add("advancements.the_beyond.gift_enadrake.title", "Building Blocks");
        add("advancements.the_beyond.gift_enadrake.description", "Gift an Enadrake an item");

        add("advancements.the_beyond.gift_rare_enadrake.title", "Wealth and Equality");
        add("advancements.the_beyond.gift_rare_enadrake.description", "Collect the Enadrake Flare block after gifting an Enadrake an item of epic rarity");

        add("advancements.the_beyond.complete_refuge.title", "Growth and Infrastructure");
        add("advancements.the_beyond.complete_refuge.description", "Place a Refuge near an Enadrake village and supply them with enough items to activate it");

        add("advancements.the_beyond.use_refuge.title", "More like new management");
        add("advancements.the_beyond.use_refuge.description", "Activate a Refuge and protect a 9x9 chunk area");

        add("advancements.the_beyond.full_power_magnet.title", "Slingshot");
        add("advancements.the_beyond.full_power_magnet.description", "Use a Magnet to pull yourself somewhere 32 blocks away");

        add("advancements.the_beyond.geyser.title", "Yoppa!");
        add("advancements.the_beyond.geyser.description", "Get launched by a giant Gauss Vent Geyser");

        add("advancements.the_beyond.casting.title", "Manual tinkering");
        add("advancements.the_beyond.casting.description", "Cast a Brittle Metal Tool from raw Brittle Metal blocks");

        add("advancements.the_beyond.freeze.title", "Down to Prismuth");
        add("advancements.the_beyond.freeze.description", "Quickly cool down a Molten Metal block with Water or Ice");

        add("advancements.the_beyond.snapshot.title", "My first adventure");
        add("advancements.the_beyond.snapshot.description", "Take a snapshot using the Prismograph");

        add("advancements.the_beyond.memory_full.title", "Memory full");
        add("advancements.the_beyond.memory_full.description", "Completely fill your Memory bank with Snapshots");

        add("advancements.the_beyond.fill_projector.title", "Pay attention");
        add("advancements.the_beyond.fill_projector.description", "Place an item inside a lit Projector");

        add("advancements.the_beyond.discover_projection.title", "Silent movie");
        add("advancements.the_beyond.discover_projection.description", "Complete a projection puzzle");

        add("advancements.the_beyond.discover_all_projection.title", "From You 2000 Years Ago");
        add("advancements.the_beyond.discover_all_projection.description", "Complete all projection puzzles");

        add("advancements.the_beyond.encounter_stalker.title", "Ambush");
        add("advancements.the_beyond.encounter_stalker.description", "Get ambushed by a Stalker");

        add("advancements.the_beyond.walk_the_rope.title", "Walk the Rope");
        add("advancements.the_beyond.walk_the_rope.description", "Use a Coiled Stalk to climb or cross to a nearby island");

        add("advancements.the_beyond.collect_pearl.title", "Toy");
        add("advancements.the_beyond.collect_pearl.description", "Pick up a Pearl bead");

        add("advancements.the_beyond.strip_mirror.title", "Victim to Vanity");
        add("advancements.the_beyond.strip_mirror.description", "Strip all six sides of a Mirror");

        add("advancements.the_beyond.tame_bauble.title", "My Peas");
        add("advancements.the_beyond.tame_bauble.description", "Tame a bauble using a Dye item");

        add("advancements.the_beyond.bucket_trinket.title", "Thrift shopping");
        add("advancements.the_beyond.bucket_trinket.description", "Collect a tamed Trinket using a bucket");

        add("advancements.the_beyond.select_trinket.title", "Song of Storms");
        add("advancements.the_beyond.select_trinket.description", "Use an Ocarina to select nearby Trinkets");

        add("advancements.the_beyond.wax_trinket.title", "Minimalist Home Decor");
        add("advancements.the_beyond.wax_trinket.description", "Use Honeycomb on a tamed Trinket to keep it as is forever");

        add("advancements.the_beyond.rake_nacre.title", "The 15th Stone");
        add("advancements.the_beyond.rake_nacre.description", "Use a Hoe to rake any kind of Nacre");

        add("advancements.the_beyond.clinamen.title", "Clinamen");
        add("advancements.the_beyond.clinamen.description", "Throw multiple pearls into Water and listen to their song");

        add("advancements.the_beyond.create_bauble.title", "Everturning");
        add("advancements.the_beyond.create_bauble.description", "Throw a pearl into Gellid Void and create a Bauble");

        //CONFIG
        add("the_beyond.configuration.visuals", "Visual config");
        add("the_beyond.configuration.mirror", "Mirror config");
        add("the_beyond.configuration.gameplay", "Gameplay");

        add("the_beyond.configuration.deafening", "Deafening effect config");
        add("the_beyond.configuration.aeronautics", "Aeronautics config");

        add("the_beyond.configuration.disengageOnConeExit", "Disengage on cone exit");
        add("the_beyond.configuration.localCap", "Local cap");
        add("the_beyond.configuration.globalCap", "Global cap");
        add("the_beyond.configuration.wardenEnrageAnger", "Warden enrage anger");
        add("the_beyond.configuration.wardenSmellRadius", "Warden smell radius");
        add("the_beyond.configuration.voidSeaOffsetAboveFloor", "Void Sea offset above floor");

        add("the_beyond.configuration.DropTotemOfRespite", "Totem of Respite drops");
        add("the_beyond.configuration.PrismographResolution", "Prismograph resolution");
        add("the_beyond.configuration.PrismographPosterization", "Prismograph posterization");

        add("the_beyond.config.enable_custom_fog", "Custom fog");
        add("the_beyond.config.clamp_lightmap", "Clamp lightmap");
        add("the_beyond.config.enable_custom_sky", "Custom sky");
        add("the_beyond.config.enable_swirling_clouds", "Swirling main island clouds");
        add("the_beyond.config.mirror_occlusion_model_based", "Mirror occlusion model based");
    }

    /** Keys already added, so a duplicate is skipped instead of LanguageProvider throwing. */
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
