package com.thebeyond.mixin;

import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.util.RefugeChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
            RefugeChunkData data = serverLevel.getChunkAt(playerPos).getData(BeyondAttachments.REFUGE_DATA);

            if (data.shouldPreventHunger()) {
                ci.cancel();
            }
        }
    }
}
