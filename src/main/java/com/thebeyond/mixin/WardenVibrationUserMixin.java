package com.thebeyond.mixin;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.deafening.Deafening;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A deaf Warden rejects every vibration here, leaving smell as its only sense. */
@Mixin(targets = "net.minecraft.world.entity.monster.warden.Warden$VibrationUser")
public abstract class WardenVibrationUserMixin {

    /** Synthetic outer-instance field. */
    @Shadow(remap = false)
    @Final
    Warden this$0;

    @Inject(method = "canReceiveVibration", at = @At("HEAD"), cancellable = true)
    private void beyond$deafWardenHearsNothing(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent,
                                               GameEvent.Context context, CallbackInfoReturnable<Boolean> cir) {
        if (Deafening.isDeafened(this$0)) {
            cir.setReturnValue(false);
            if (this$0.tickCount % 40 == 0) { // throttled: confirms vibrations are being dropped
                TheBeyond.LOGGER.debug("[Deafening] deaf Warden ignored a vibration");
            }
        }
    }
}
