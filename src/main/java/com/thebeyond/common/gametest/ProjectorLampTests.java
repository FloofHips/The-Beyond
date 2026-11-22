package com.thebeyond.common.gametest;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.ProjectorBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheBeyond.MODID)
@PrefixGameTestTemplate(false)
public final class ProjectorLampTests {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final BlockPos PROJECTOR = new BlockPos(3, 2, 2);
    private static final BlockPos LAMP = new BlockPos(2, 2, 2);
    private static final BlockPos LEVER = new BlockPos(1, 2, 2);
    private static final BlockPos SIDE = new BlockPos(2, 2, 1);

    private ProjectorLampTests() {
    }

    @GameTest(template = "projector_bench", timeoutTicks = 40)
    public static void leverOffDarkensProjector(final GameTestHelper helper) {
        setUp(helper);
        helper.setBlock(LEVER, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.WEST));
        helper.pullLever(LEVER);
        helper.runAfterDelay(2, () -> {
            log(helper, "lever on");
            helper.assertTrue(projectorLit(helper), "lever on: projector dark behind a lit lamp");
            helper.pullLever(LEVER);
        });
        helper.runAfterDelay(12, () -> {
            log(helper, "lever off");
            helper.assertFalse(projectorLit(helper), "lever off: projector lit behind an unlit lamp");
            helper.succeed();
        });
    }

    @GameTest(template = "projector_bench", timeoutTicks = 40)
    public static void redstoneBlockSwitchesProjector(final GameTestHelper helper) {
        setUp(helper);
        helper.setBlock(SIDE, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
            log(helper, "redstone block placed");
            helper.assertTrue(projectorLit(helper), "block placed: projector dark behind a lit lamp");
            helper.setBlock(SIDE, Blocks.AIR);
        });
        helper.runAfterDelay(12, () -> {
            log(helper, "redstone block removed");
            helper.assertFalse(projectorLit(helper), "block removed: projector lit behind an unlit lamp");
            helper.succeed();
        });
    }

    private static void setUp(GameTestHelper helper) {
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        helper.setBlock(PROJECTOR, BeyondBlocks.PROJECTOR.get().defaultBlockState().setValue(ProjectorBlock.FACING, Direction.EAST));
    }

    private static boolean projectorLit(GameTestHelper helper) {
        return helper.getBlockState(PROJECTOR).getValue(ProjectorBlock.POWERED);
    }

    private static void log(GameTestHelper helper, String step) {
        LOGGER.info("[gametest] projector {}: lamp lit={} projector lit={}", step,
                helper.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT), projectorLit(helper));
    }
}
