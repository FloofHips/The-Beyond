package com.thebeyond.data;

import com.thebeyond.TheBeyond;
import com.thebeyond.data.assets.BlockStates;
import com.thebeyond.data.assets.ItemModels;
import com.thebeyond.data.assets.Lang;
import com.thebeyond.data.tags.BeyondBlockTags;
import com.thebeyond.data.tags.BeyondDamageTypeTags;
import com.thebeyond.data.tags.BeyondEntityTypeTags;
import com.thebeyond.data.tags.BeyondItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
@EventBusSubscriber(modid = TheBeyond.MODID)
public class Generators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        ExistingFileHelper helper = event.getExistingFileHelper();
        RegistrySetBuilder registrySetBuilder = new RegistrySetBuilder();

        BeyondBlockTags blockTags = new BeyondBlockTags(output, lookupProvider, helper);
        generator.addProvider(event.includeServer(), blockTags);
        generator.addProvider(event.includeServer(), new BeyondItemTags(output, lookupProvider, blockTags.contentsGetter(), helper));
        generator.addProvider(event.includeServer(), new BeyondEntityTypeTags(output, lookupProvider, helper));
        generator.addProvider(event.includeServer(), new BeyondDamageTypeTags(output, lookupProvider, helper));

        DatapackBuiltinEntriesProvider datapackProvider = new DatapackBuiltinEntriesProvider(output, lookupProvider, registrySetBuilder, Set.of(TheBeyond.MODID));
        CompletableFuture<HolderLookup.Provider> builtinLookupProvider = datapackProvider.getRegistryProvider();
        generator.addProvider(event.includeServer(), datapackProvider);

        generator.addProvider(event.includeServer(), new BeyondRecipes(output, lookupProvider));
        generator.addProvider(event.includeServer(), new BeyondDataMaps(output, lookupProvider));
        generator.addProvider(event.includeServer(), new BeyondAdvancements(output, lookupProvider, helper));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Collections.emptySet(), List.of(
                new LootTableProvider.SubProviderEntry(BeyondBlockLoot::new, LootContextParamSets.BLOCK)
        ), lookupProvider));

        BlockStates blockStates = new BlockStates(output, helper);
        generator.addProvider(event.includeClient(), blockStates);
        generator.addProvider(event.includeClient(), new ItemModels(output, blockStates.models().existingFileHelper));
        generator.addProvider(event.includeClient(), new Lang(output));
    }
}
