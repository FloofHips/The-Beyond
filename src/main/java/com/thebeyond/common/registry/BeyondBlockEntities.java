package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.blockentities.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, TheBeyond.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MemorFaucetBlockEntity>> MEMOR_FAUCET = BLOCK_ENTITY_TYPES.
            register("memor_faucet", () -> BlockEntityType.Builder.of(MemorFaucetBlockEntity::new, BeyondBlocks.MEMOR_FAUCET.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BonfireBlockEntity>> BONFIRE = BLOCK_ENTITY_TYPES.
            register("bonfire", () -> BlockEntityType.Builder.of(BonfireBlockEntity::new, BeyondBlocks.BONFIRE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnadrakeHutBlockEntity>> ENADRAKE_HUT = BLOCK_ENTITY_TYPES.
            register("enadrake_hut", () -> BlockEntityType.Builder.of(EnadrakeHutBlockEntity::new, BeyondBlocks.ENADRAKE_HUT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefugeBlockEntity>> REFUGE = BLOCK_ENTITY_TYPES.
            register("refuge", () -> BlockEntityType.Builder.of(RefugeBlockEntity::new, BeyondBlocks.REFUGE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MirrorBlockEntity>> MIRROR = BLOCK_ENTITY_TYPES.
            register("mirror", () -> BlockEntityType.Builder.of(MirrorBlockEntity::new,
                    BeyondBlocks.PEARL_MIRROR.get(),
                    BeyondBlocks.ORNATE_MIRROR.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProjectorBlockEntity>> PROJECTOR = BLOCK_ENTITY_TYPES.
            register("projector", () -> BlockEntityType.Builder.of(ProjectorBlockEntity::new, BeyondBlocks.PROJECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrismographBlockEntity>> PRISMOGRAPH = BLOCK_ENTITY_TYPES.
            register("prismograph", () -> BlockEntityType.Builder.of(PrismographBlockEntity::new, BeyondBlocks.PRISMOGRAPH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BellowBlockEntity>> BELLOW = BLOCK_ENTITY_TYPES.
            register("bellow", () -> BlockEntityType.Builder.of(BellowBlockEntity::new, BeyondBlocks.BELLOW.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PerkaStalkMouthBlockEntity>> PERKA_STALK_MOUTH = BLOCK_ENTITY_TYPES.
            register("perka_stalk_mouth", () -> BlockEntityType.Builder.of(PerkaStalkMouthBlockEntity::new, BeyondBlocks.PERKA_STALK_MOUTH.get()).build(null));

}
