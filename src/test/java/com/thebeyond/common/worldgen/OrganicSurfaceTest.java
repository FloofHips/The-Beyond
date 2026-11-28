package com.thebeyond.common.worldgen;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganicSurfaceTest {
    @Test
    void naturalSolidsCorruptPlantsHideAndTheRestStays() {
        for (BlockState s : new BlockState[]{Blocks.END_STONE.defaultBlockState(), Blocks.RED_WOOL.defaultBlockState(),
                BeyondBlocks.PLATED_END_STONE.get().defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState()}) {
            assertTrue(OrganicSurface.ground(s), s.toString());
            assertFalse(OrganicSurface.plant(s), s.toString());
        }
        for (BlockState s : new BlockState[]{Blocks.POPPY.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState()}) {
            assertTrue(OrganicSurface.plant(s), s.toString());
            assertFalse(OrganicSurface.ground(s), s.toString());
        }
        for (BlockState s : new BlockState[]{Blocks.CHEST.defaultBlockState(), Blocks.END_GATEWAY.defaultBlockState(),
                Blocks.SCULK.defaultBlockState(), Blocks.COAL_BLOCK.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.WATER.defaultBlockState(), Blocks.AIR.defaultBlockState(), BeyondBlocks.BONFIRE.get().defaultBlockState(),
                OrganicExperiment.state(OrganicNetwork.TUBE, 5, false), OrganicExperiment.state(OrganicNetwork.BULB, 40, false)}) {
            assertFalse(OrganicSurface.ground(s) || OrganicSurface.plant(s), s.toString());
        }
    }
}
