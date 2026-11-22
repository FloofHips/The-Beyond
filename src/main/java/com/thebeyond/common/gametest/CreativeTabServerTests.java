package com.thebeyond.common.gametest;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondTabs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;

@GameTestHolder(TheBeyond.MODID)
@PrefixGameTestTemplate(false)
public final class CreativeTabServerTests {

    private CreativeTabServerTests() {
    }

    @GameTest(template = "bedazzled_bench")
    public static void beyondTabBuildsOnServer(final GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CreativeModeTab tab = BeyondTabs.THE_BEYOND.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(server.getWorldData().enabledFeatures(), false,
                server.registryAccess()));
        Collection<ItemStack> items = tab.getDisplayItems();
        helper.assertTrue(items.stream().anyMatch(stack -> stack.is(BeyondBlocks.MEMOR.get().asItem())),
                "the Beyond tab built on the server has " + items.size() + " items and no memor");
        helper.succeed();
    }
}
