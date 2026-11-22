package com.thebeyond.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FallKickGuardMixin {
    @Shadow public ServerPlayer player;
    @Shadow private double lastGoodY;
    @Shadow private boolean clientIsFloating;

    @Inject(method = "handleMovePlayer", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clientIsFloating:Z"))
    private void the_beyond$fallIsNotFlight(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (this.player.getY() - this.lastGoodY < -0.03125) this.clientIsFloating = false;
    }
}
