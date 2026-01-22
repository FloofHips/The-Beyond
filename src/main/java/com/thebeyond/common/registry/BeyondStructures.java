package com.thebeyond.common.registry;

import com.google.common.collect.ImmutableSet;
import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public class BeyondStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(BuiltInRegistries.STRUCTURE_TYPE, TheBeyond.MODID);
}
