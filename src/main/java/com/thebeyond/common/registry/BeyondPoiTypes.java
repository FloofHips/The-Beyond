package com.thebeyond.common.registry;

import com.google.common.collect.ImmutableSet;
import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public class BeyondPoiTypes {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, TheBeyond.MODID);

    public static final DeferredHolder<PoiType, PoiType> BONFIRE = POI_TYPES.register("bonfire", () -> new PoiType(getBlockStates(BeyondBlocks.BONFIRE.get()), 0, 10));

    private static Set<BlockState> getBlockStates(Block block) {
        return ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates());
    }
}
