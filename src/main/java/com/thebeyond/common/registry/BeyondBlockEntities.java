package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.blockentities.MemorFaucetBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, TheBeyond.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MemorFaucetBlockEntity>> MEMOR_FAUCET =
            BLOCK_ENTITY_TYPES.register("memor_faucet",
                    () -> BlockEntityType.Builder.of(MemorFaucetBlockEntity::new,
                            BeyondBlocks.MEMOR_FAUCET.get()).build(null));
}
