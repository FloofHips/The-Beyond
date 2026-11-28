package com.thebeyond.common.worldgen;

import com.thebeyond.common.block.OrganicBulbBlock;
import com.thebeyond.common.block.OrganicTissueBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chunk's tissue record survives a save, and the painter's own blocks map every kind and band. */
class OrganicTissueTest {

    @Test
    void theTissueRecordComesBackTheSameAfterSaveAndLoad() {
        OrganicTissue.Held held = new OrganicTissue.Held();
        BlockState stone = Blocks.END_STONE.defaultBlockState(), wool = Blocks.RED_WOOL.defaultBlockState();
        long cell = -4_611_686_018_427_387_000L;
        for (int i = 0; i < 300; i++) {
            int x = i % 16, z = (i / 16) % 16, y = -64 + i;
            byte kind = i % 3 == 0 ? OrganicNetwork.TUNNEL : i % 3 == 1 ? OrganicNetwork.TUBE : OrganicNetwork.BULB;
            BlockState replaced = kind != OrganicNetwork.TUNNEL ? null : i % 2 == 0 ? stone : wool;
            held.add(OrganicTissue.pack(x, y, z, -64), kind, i % 7 == 0 ? 0 : cell + i % 4, i % 7 == 0 ? -1 : i % 5, replaced);
        }
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag tag = held.serializeNBT(provider);
        OrganicTissue.Held back = new OrganicTissue.Held();
        back.deserializeNBT(provider, tag);
        assertEquals(held.size(), back.size());
        for (int i = 0; i < held.size(); i++) {
            assertEquals(held.packed(i), back.packed(i));
            assertEquals(held.kind(i), back.kind(i));
            assertEquals(held.cell(i), back.cell(i));
            assertEquals(held.organ(i), back.organ(i));
            assertEquals(held.replaced(i), back.replaced(i));
        }
        assertEquals(tag, back.serializeNBT(provider), "the same tag again");
        assertEquals(stone, back.replaced(0));
        assertNull(back.replaced(1));
        assertEquals(-1, back.organ(0), "no bulb");
        assertEquals((383 << 8) | (15 << 4) | 15, OrganicTissue.pack(31, 319, -1, -64));
    }

    @Test
    void widthsSurviveASaveAndOldSavesReadAsUnknown() {
        OrganicTissue.Held held = new OrganicTissue.Held();
        for (int i = 0; i < 40; i++) {
            held.add(OrganicTissue.pack(i % 16, 64 + i / 16, 5, -64), OrganicNetwork.TUBE, 7L, 1, null, (byte) (i % 13));
        }
        held.add(OrganicTissue.pack(3, 90, 3, -64), OrganicNetwork.TUBE, 7L, 1, null);
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag tag = held.serializeNBT(provider);
        OrganicTissue.Held back = new OrganicTissue.Held();
        back.deserializeNBT(provider, tag);
        for (int i = 0; i < 40; i++) assertEquals(i % 13, back.thick(i));
        assertEquals(-1, back.thick(40), "added without a width");
        tag.remove("thick");
        OrganicTissue.Held old = new OrganicTissue.Held();
        old.deserializeNBT(provider, tag);
        for (int i = 0; i < old.size(); i++) assertEquals(-1, old.thick(i), "a save from before widths");
    }

    @Test
    void dripsSurviveASaveAndOldSavesHaveNone() {
        OrganicTissue.Held held = new OrganicTissue.Held();
        byte code = OrganicHalo.drip(9, 2);
        held.add(OrganicTissue.pack(3, 90, 3, -64), OrganicNetwork.TUBE, 7L, 1, null, (byte) 4, code);
        held.add(OrganicTissue.pack(4, 90, 3, -64), OrganicNetwork.TUBE, 7L, 1, null, (byte) 4);
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag tag = held.serializeNBT(provider);
        OrganicTissue.Held back = new OrganicTissue.Held();
        back.deserializeNBT(provider, tag);
        assertEquals((int) code, back.drip(0));
        assertEquals(9, OrganicHalo.dripLength(back.drip(0)));
        assertEquals(1, OrganicHalo.dripDz(back.drip(0)));
        assertEquals(0, back.drip(1), "added without a drip");
        tag.remove("drip");
        OrganicTissue.Held old = new OrganicTissue.Held();
        old.deserializeNBT(provider, tag);
        assertEquals(0, old.drip(0), "a save from before drips");
    }

    @Test
    void ownBlocksCoverEveryKindAndBand() {
        assertTrue(OrganicExperiment.OWN_BLOCKS, "own blocks by default");
        float[] bands = {0, 60, 120, 200, 300, 500, 700, 1000, 1400, 5000};
        int[] want = {0, 1, 2, 3, 4, 5, 6, 7, 8, 8};
        for (int b = 0; b < bands.length; b++) {
            for (byte k : new byte[]{OrganicNetwork.TUBE, OrganicNetwork.CROSS, OrganicNetwork.AERIAL, OrganicNetwork.PENDANT,
                    OrganicNetwork.FILM, OrganicNetwork.TUNNEL}) {
                BlockState s = OrganicExperiment.state(k, bands[b], false);
                assertSame(k == OrganicNetwork.FILM ? BeyondBlocks.ORG_FILM.get() : k == OrganicNetwork.TUNNEL
                        ? BeyondBlocks.ORG_ROOT.get() : BeyondBlocks.ORG_TUBE.get(), s.getBlock(), "kind " + k);
                assertEquals(want[b], s.getValue(OrganicTissueBlock.BAND), "band of " + bands[b]);
                assertTrue(OrganicExperiment.tissue(s));
            }
        }
        for (boolean exposed : new boolean[]{false, true}) {
            BlockState n = OrganicExperiment.state(OrganicNetwork.BULB, 0, exposed);
            BlockState s = OrganicExperiment.state(OrganicNetwork.BULB, 40, exposed);
            assertSame(BeyondBlocks.ORG_NUCLEUS.get(), n.getBlock());
            assertSame(BeyondBlocks.ORG_BULB.get(), s.getBlock());
            assertEquals(exposed, n.getValue(OrganicBulbBlock.EXPOSED));
            assertEquals(exposed, s.getValue(OrganicBulbBlock.EXPOSED));
            assertTrue(OrganicExperiment.tissue(n) && OrganicExperiment.tissue(s));
        }
        assertSame(OrganicExperiment.LINE_BLOCK, OrganicExperiment.state(OrganicExperiment.LINE, 0, false).getBlock());
        assertTrue(!OrganicExperiment.tissue(Blocks.END_STONE.defaultBlockState()));
    }

    @Test
    void heldKeepsEachOrganismTableAndTheBlocksThatLeft() {
        OrganicTissue.Held held = new OrganicTissue.Held();
        long cell = 123_456_789L;
        float[] table = {1.5f, 70.5f, -3.5f, 4f, 0f, 9f, 1f, 12f, 30.5f, 72.5f, 8.5f, 2f, 1f, 9f, Float.NaN, 40f};
        held.organism(cell, "slime_mold", table);
        for (int i = 0; i < 40; i++) {
            held.add(OrganicTissue.pack(i % 16, 64 + i / 16, 3, -64), OrganicNetwork.TUBE, cell, i % 2, null);
        }
        held.remove(5);
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        OrganicTissue.Held back = new OrganicTissue.Held();
        back.deserializeNBT(provider, held.serializeNBT(provider));
        assertEquals("slime_mold", back.setup(cell));
        float[] t = back.table(cell);
        assertEquals(table.length, t.length);
        for (int i = 0; i < t.length; i++) assertEquals(Float.floatToRawIntBits(table[i]), Float.floatToRawIntBits(t[i]));
        assertEquals(OrganicTissue.GONE, back.kind(5));
        assertEquals(7, back.find(OrganicTissue.pack(7, 64, 3, -64)));
        assertEquals(-1, back.find(OrganicTissue.pack(15, 300, 15, -64)));
        assertNull(back.setup(cell + 1));
    }

    @Test
    void clearedTissueKeepsItsKindAndStateAndGrowsBack() {
        OrganicTissue.Held held = new OrganicTissue.Held();
        BlockState stone = Blocks.END_STONE.defaultBlockState();
        BlockState root = OrganicExperiment.state(OrganicNetwork.TUNNEL, 3, false);
        BlockState tube = OrganicExperiment.state(OrganicNetwork.TUBE, 5, false);
        held.add(OrganicTissue.pack(1, 70, 1, -64), OrganicNetwork.TUNNEL, 9L, 2, stone);
        held.add(OrganicTissue.pack(2, 70, 1, -64), OrganicNetwork.TUBE, 9L, 2, null);
        held.add(OrganicTissue.pack(3, 70, 1, -64), OrganicNetwork.TUBE, 9L, 2, null);
        held.clear(0, root);
        held.clear(1, tube);
        held.remove(2);
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        OrganicTissue.Held back = new OrganicTissue.Held();
        back.deserializeNBT(provider, held.serializeNBT(provider));
        assertTrue(back.cleared(0) && back.cleared(1) && back.gone(2) && !back.cleared(2));
        assertSame(stone, back.replaced(0));
        assertSame(root, back.regrow(0));
        assertEquals(OrganicNetwork.TUNNEL, back.kind(0));
        assertSame(tube, back.regrow(1));
        assertEquals(OrganicNetwork.TUBE, back.kind(1));
        assertNull(back.regrow(2));
        assertEquals(OrganicTissue.GONE, back.kind(2));
    }
}
