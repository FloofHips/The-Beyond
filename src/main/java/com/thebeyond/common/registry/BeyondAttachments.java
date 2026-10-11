package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.awareness.PlayerAwareness;
import com.thebeyond.common.worldgen.GellidLakeFill;
import com.thebeyond.util.RefugeChunkData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class BeyondAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TheBeyond.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RefugeChunkData>> REFUGE_DATA = ATTACHMENT_TYPES.register("refuge_data",
            () -> AttachmentType.serializable(RefugeChunkData::new).build());

    /** Gellid lake cells a chunk keeps until it and its neighbours are complete. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GellidLakeFill.Held>> HELD_LAKE = ATTACHMENT_TYPES.register("held_lake",
            () -> AttachmentType.serializable(GellidLakeFill.Held::new).build());

    /** Per-player unlocked awareness keys. {@code copyOnDeath()} so discoveries survive respawn. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerAwareness>> PLAYER_AWARENESS = ATTACHMENT_TYPES.register("player_awareness",
            () -> AttachmentType.serializable(PlayerAwareness::new).copyOnDeath().build());
}
