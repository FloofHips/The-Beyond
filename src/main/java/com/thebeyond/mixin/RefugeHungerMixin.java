package com.thebeyond.mixin;

import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.util.RefugeChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class RefugeHungerMixin {
    @Inject(at = @At("HEAD"), method = "causeFoodExhaustion", cancellable = true)
    public void beyond$causeFoodExhaustion(float exhaustion, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        Level level = player.level();

        if (level.isClientSide) return;
        if ((level instanceof ServerLevel serverLevel)) {
            BlockPos playerPos = player.blockPosition();
            // Non-creating lookup: calling getChunkAt (create=true) from here can re-enter
            // chunk loading while DistanceManager.runAllUpdates is iterating, causing a
            // ConcurrentModificationException.
            ChunkAccess chunk = serverLevel.getChunkSource()
                    .getChunk(playerPos.getX() >> 4, playerPos.getZ() >> 4, false);
            if (chunk != null) {
                RefugeChunkData data = chunk.getData(BeyondAttachments.REFUGE_DATA);
                if (data.shouldPreventHunger()) { ci.cancel(); return; }
            }
            // Sub-level fallback: refuge attachment lives at the remote plot storage offset.
            BlockPos stored = com.thebeyond.common.compat.BeyondCompatHooks.storedForVisible(serverLevel, playerPos);
            if (stored != null) {
                ChunkAccess sChunk = serverLevel.getChunkSource()
                        .getChunk(stored.getX() >> 4, stored.getZ() >> 4, false);
                if (sChunk != null && sChunk.getData(BeyondAttachments.REFUGE_DATA).shouldPreventHunger()) {
                    ci.cancel();
                }
            }
        }
    }
}
