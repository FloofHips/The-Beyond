package com.thebeyond.mixin;

import com.thebeyond.BeyondConfig;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.deafening.Deafening;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Deafening for the Warden: increaseAngerAt is where all its anger passes, so new anger stops without a recall. */
@Mixin(Warden.class)
public abstract class WardenDeafenAngerMixin {

    /** Anger offset of a direct hit, far above vibration's, so a deaf Warden still reacts when hit. */
    private static final int DAMAGE_ANGER_OFFSET = 100;

    @Inject(method = "increaseAngerAt(Lnet/minecraft/world/entity/Entity;IZ)V", at = @At("HEAD"), cancellable = true)
    private void beyond$suppressPlayerAngerWhenDeaf(Entity entity, int offset, boolean playListeningSound, CallbackInfo ci) {
        Warden self = (Warden) (Object) this;
        if (offset < DAMAGE_ANGER_OFFSET
                && entity instanceof Player
                && Deafening.isDeafened(self)
                && self.distanceTo(entity) > BeyondConfig.WARDEN_SMELL_RADIUS.get()) {
            ci.cancel();
            if (self.tickCount % 40 == 0) { // throttle: confirms the suppression is firing without per-tick spam
                TheBeyond.LOGGER.debug("[Deafening] deafened Warden ignored player vibration anger (beyond smell radius)");
            }
        }
    }
}
