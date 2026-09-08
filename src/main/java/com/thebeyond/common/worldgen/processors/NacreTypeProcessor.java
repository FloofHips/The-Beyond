package com.thebeyond.common.worldgen.processors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.SimpleMapCodec;
import com.thebeyond.common.block.AmphoraBlock;
import com.thebeyond.common.block.PolarAntennaBlock;
import com.thebeyond.common.block.RakedNacreBlock;
import com.thebeyond.common.block.blockstates.SizeProperty;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondProcessors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static net.minecraft.world.level.levelgen.structure.Structure.simpleCodec;

public class NacreTypeProcessor extends StructureProcessor {
    public static final MapCodec<NacreTypeProcessor> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.optionalFieldOf("nacre_type", "none").forGetter(p -> p.state)
            ).apply(instance, NacreTypeProcessor::new)
    );
    public String state;

    private NacreTypeProcessor(String type) {
        this.state = type;
    }

    @Override
    public @Nullable StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, StructureTemplate.StructureBlockInfo blockInfo, StructureTemplate.StructureBlockInfo relativeBlockInfo, StructurePlaceSettings settings) {
        BlockState oldState = relativeBlockInfo.state();

        if (!(state.equals("none")) && oldState.getBlock() instanceof RakedNacreBlock) {
            BlockState newState = getBlockStateFromString(state).setValue(RakedNacreBlock.RAKE_DIRECTION, oldState.getValue(RakedNacreBlock.RAKE_DIRECTION));
            return new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(), newState, relativeBlockInfo.nbt());
        }

        return relativeBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return BeyondProcessors.NACRE_TYPE.get();
    }

    public BlockState getBlockStateFromString(String state) {
        if (Objects.equals(state, "pale")) return BeyondBlocks.PALE_RAKED_NACRE.get().defaultBlockState();
        if (Objects.equals(state, "rich")) return BeyondBlocks.RICH_RAKED_NACRE.get().defaultBlockState();
        return BeyondBlocks.RAKED_NACRE.get().defaultBlockState();
    }
}

