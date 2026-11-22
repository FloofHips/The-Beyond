package com.thebeyond.common.gametest;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.PearlItemEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheBeyond.MODID)
@PrefixGameTestTemplate(false)
public final class BedazzledEndStoneTests {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final BlockPos STONE = new BlockPos(3, 2, 2);
    private static final BlockPos PISTON = new BlockPos(2, 2, 2);
    private static final BlockPos POWER = new BlockPos(1, 2, 2);
    private static final BlockPos PUSHED = new BlockPos(4, 2, 2);

    private BedazzledEndStoneTests() {
    }

    @GameTest(template = "bedazzled_bench")
    public static void silkTouchKeepsPearls(final GameTestHelper helper) {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        breakAndCount(helper, "silk_touch", GameType.SURVIVAL, pick, false, 1);
    }

    @GameTest(template = "bedazzled_bench")
    public static void pickaxeReleasesPearls(final GameTestHelper helper) {
        breakAndCount(helper, "pickaxe", GameType.SURVIVAL, new ItemStack(Items.DIAMOND_PICKAXE), true, 0);
    }

    @GameTest(template = "bedazzled_bench")
    public static void handReleasesPearls(final GameTestHelper helper) {
        breakAndCount(helper, "hand", GameType.SURVIVAL, ItemStack.EMPTY, true, 0);
    }

    @GameTest(template = "bedazzled_bench")
    public static void creativeReleasesNoPearls(final GameTestHelper helper) {
        breakAndCount(helper, "creative", GameType.CREATIVE, new ItemStack(Items.DIAMOND_PICKAXE), false, 0);
    }

    @GameTest(template = "bedazzled_bench")
    public static void explosionReleasesPearls(final GameTestHelper helper) {
        helper.setBlock(STONE, BeyondBlocks.BEDAZZLED_END_STONE.get());
        Vec3 center = helper.absoluteVec(Vec3.atCenterOf(STONE));
        helper.getLevel().explode(null, center.x, center.y, center.z, 2.5F, Level.ExplosionInteraction.TNT);
        int pearls = pearls(helper);
        LOGGER.info("[gametest] bedazzled explosion: block gone={} pearls={}", helper.getBlockState(STONE).isAir(), pearls);
        helper.assertTrue(helper.getBlockState(STONE).isAir(), "explosion left the block");
        helper.assertTrue(pearls > 0, "explosion released no pearls");
        helper.succeed();
    }

    @GameTest(template = "bedazzled_bench", timeoutTicks = 40)
    public static void pistonMovesWithoutPearls(final GameTestHelper helper) {
        helper.setBlock(STONE, BeyondBlocks.BEDAZZLED_END_STONE.get());
        helper.setBlock(PISTON, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(6, () -> {
            boolean moved = helper.getBlockState(PUSHED).is(BeyondBlocks.BEDAZZLED_END_STONE.get());
            int pearls = pearls(helper);
            LOGGER.info("[gametest] bedazzled piston: moved={} pearls={}", moved, pearls);
            helper.assertTrue(moved, "piston did not move the block");
            helper.assertTrue(pearls == 0, "piston push released " + pearls + " pearls");
            helper.succeed();
        });
    }

    private static void breakAndCount(GameTestHelper helper, String label, GameType mode, ItemStack tool,
                                      boolean expectPearls, int expectBlockItems) {
        helper.setBlock(STONE, BeyondBlocks.BEDAZZLED_END_STONE.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        player.moveTo(helper.absoluteVec(new Vec3(3.5, 2.0, 4.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        player.gameMode.destroyBlock(helper.absolutePos(STONE));
        int pearls = pearls(helper);
        int blockItems = blockItems(helper);
        LOGGER.info("[gametest] bedazzled {}: block gone={} pearls={} block items={}",
                label, helper.getBlockState(STONE).isAir(), pearls, blockItems);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(helper.getBlockState(STONE).isAir(), label + ": block was not broken");
        helper.assertTrue(expectPearls ? pearls > 0 : pearls == 0, label + ": pearls=" + pearls);
        helper.assertTrue(blockItems == expectBlockItems, label + ": block items=" + blockItems);
        helper.succeed();
    }

    private static int pearls(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(PearlItemEntity.class, helper.getBounds().inflate(3)).size();
    }

    private static int blockItems(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3),
                e -> e.getItem().is(BeyondBlocks.BEDAZZLED_END_STONE.get().asItem())).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
    }
}
