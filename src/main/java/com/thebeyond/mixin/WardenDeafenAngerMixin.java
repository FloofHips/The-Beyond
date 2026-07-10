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

/** Warden branch of deafening stealth: {@code increaseAngerAt} is the one choke point all vibration- and
 *  damage-driven anger routes through, so gating there stops fresh anger without calling off an already-hunting Warden. */
@Mixin(Warden.class)
public abstract class WardenDeafenAngerMixin {

    /** Direct-damage anger offset; vibration/roar offsets are much smaller (10/20/35), so filtering below
     *  this threshold leaves a deaf Warden still aggroing when actually hit. */
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
