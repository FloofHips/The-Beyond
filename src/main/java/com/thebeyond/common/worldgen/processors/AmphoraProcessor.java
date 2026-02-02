package com.thebeyond.common.worldgen.processors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.common.block.AmphoraBlock;
import com.thebeyond.common.block.blockstates.SizeProperty;
import com.thebeyond.common.registry.BeyondProcessors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

public class AmphoraProcessor extends StructureProcessor {
    private final float smallWeight;
    private final float mediumWeight;
    private final float largeWeight;

    public static final MapCodec<AmphoraProcessor> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.FLOAT.optionalFieldOf("small_weight", 0.5f).forGetter(p -> p.smallWeight),
                    Codec.FLOAT.optionalFieldOf("medium_weight", 0.35f).forGetter(p -> p.mediumWeight),
                    Codec.FLOAT.optionalFieldOf("large_weight", 0.15f).forGetter(p -> p.largeWeight)
            ).apply(instance, AmphoraProcessor::new)
    );

    public AmphoraProcessor(float smallWeight, float mediumWeight, float largeWeight) {
        this.smallWeight = smallWeight;
        this.mediumWeight = mediumWeight;
        this.largeWeight = largeWeight;
    }

    @Override
    public @Nullable StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, StructureTemplate.StructureBlockInfo blockInfo, StructureTemplate.StructureBlockInfo relativeBlockInfo, StructurePlaceSettings settings) {
        BlockState state = relativeBlockInfo.state();

        if (state.getBlock() instanceof AmphoraBlock) {
            RandomSource random = settings.getRandom(relativeBlockInfo.pos());

            float total = smallWeight + mediumWeight + largeWeight;
            float roll = random.nextFloat() * total;

            SizeProperty size;
            if (roll < smallWeight) {
                size = SizeProperty.SMALL;
            } else if (roll < smallWeight + mediumWeight) {
                size = SizeProperty.MEDIUM;
            } else {
                size = SizeProperty.LARGE;
            }

            BlockState newState = state.setValue(AmphoraBlock.SIZE, size);
            return new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(), newState, relativeBlockInfo.nbt());
        }

        return relativeBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return BeyondProcessors.AMPHORA_SIZE.get();
    }
}
