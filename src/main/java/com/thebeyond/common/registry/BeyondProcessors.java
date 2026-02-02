package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.processors.AmphoraProcessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondProcessors {
    public static final DeferredRegister<StructureProcessorType<?>> PROCESSOR_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, TheBeyond.MODID);

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<AmphoraProcessor>> AMPHORA_SIZE = PROCESSOR_TYPES.register("amphora_size", () -> () -> AmphoraProcessor.CODEC);

    public static void register(IEventBus eventBus) {
        PROCESSOR_TYPES.register(eventBus);
    }
}
