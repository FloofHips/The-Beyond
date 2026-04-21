package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.knowledge.PlayerKnowledge;
import com.thebeyond.util.RefugeChunkData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class BeyondAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TheBeyond.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RefugeChunkData>> REFUGE_DATA = ATTACHMENT_TYPES.register("refuge_data",
            () -> AttachmentType.serializable(RefugeChunkData::new).build());

    /** Per-player unlocked knowledge keys. {@code copyOnDeath()} so discoveries survive respawn. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerKnowledge>> PLAYER_KNOWLEDGE = ATTACHMENT_TYPES.register("player_knowledge",
            () -> AttachmentType.serializable(PlayerKnowledge::new).copyOnDeath().build());
}
