package com.thebeyond.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

public class TeleportUtils {
    public static void randomTeleport(Level level, LivingEntity entityLiving) {
        if (!level.isClientSide) {
            int edge = 20;
            for(int i = 0; i < edge; ++i) {
                double d0 = entityLiving.getX() + (entityLiving.getRandom().nextDouble() - 0.5) * edge;
                double d1 = Mth.clamp(entityLiving.getY() + (double)(entityLiving.getRandom().nextInt(edge) - 8), (double)level.getMinBuildHeight(), (double)(level.getMinBuildHeight() + ((ServerLevel)level).getLogicalHeight() - 1));
                double d2 = entityLiving.getZ() + (entityLiving.getRandom().nextDouble() - 0.5) * edge;
                if (entityLiving.isPassenger()) {
                    entityLiving.stopRiding();
                }

                Vec3 vec3 = entityLiving.position();
                EntityTeleportEvent.ChorusFruit event = EventHooks.onChorusFruitTeleport(entityLiving, d0, d1, d2);
                if (event.isCanceled()) {
                    return;
                }

                if (entityLiving.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true)) {
                    level.gameEvent(GameEvent.TELEPORT, vec3, GameEvent.Context.of(entityLiving));

                    SoundEvent soundevent = SoundEvents.CHORUS_FRUIT_TELEPORT;
                    SoundSource soundsource = entityLiving.getSoundSource();


                    level.playSound((Player)null, entityLiving.getX(), entityLiving.getY(), entityLiving.getZ(), soundevent, soundsource);
                    entityLiving.resetFallDistance();
                    break;
                }
            }

            if (entityLiving instanceof Player) {
                Player player = (Player)entityLiving;
                player.resetCurrentImpulseContext();
            }
        }
    }
}
