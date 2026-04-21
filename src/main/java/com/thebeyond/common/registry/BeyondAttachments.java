package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.activation.ActivationChunkData;
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

    /**
     * Per-chunk activation state for biome interactivity (Polar chain, Bismuth
     * freeze, Perka triggers, Legacy Grove excavation, future mechanics).
     * See {@link com.thebeyond.common.activation.BeyondActivation} for the
     * static API callers should use. No existing code consumes this yet —
     * it is registered ahead of content sprints so save/load is already
     * plumbed when the first caller arrives. Runtime cost when unused: an
     * empty {@code CompoundTag} per chunk at save, nothing at tick time.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ActivationChunkData>> ACTIVATION_DATA = ATTACHMENT_TYPES.register("activation_data",
            () -> AttachmentType.serializable(ActivationChunkData::new).build());

    /**
     * Per-player knowledge set (which Farlands/Wall/Beyond gates the player
     * has unlocked). See {@link com.thebeyond.common.knowledge.BeyondKnowledge}
     * for the static API; scaffolding only — no content is currently gated.
     * {@code copyOnDeath()} ensures discovery persists across respawns, since
     * "you already saw the Farlands" should not be reset by dying.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerKnowledge>> PLAYER_KNOWLEDGE = ATTACHMENT_TYPES.register("player_knowledge",
            () -> AttachmentType.serializable(PlayerKnowledge::new).copyOnDeath().build());
}
