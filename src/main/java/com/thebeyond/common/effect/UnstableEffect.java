package com.thebeyond.common.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

public class UnstableEffect extends MobEffect {
    public UnstableEffect(MobEffectCategory category, int color) {
        super(category, color, ParticleTypes.PORTAL);
    }

    @Override
    public void onMobHurt(LivingEntity entityLiving, int amplifier, DamageSource damageSource, float amount) {
        Level level = entityLiving.level();
        if (!level.isClientSide) {
            for(int i = 0; i < 16; ++i) {
                double d0 = entityLiving.getX() + (entityLiving.getRandom().nextDouble() - 0.5) * 16.0;
                double d1 = Mth.clamp(entityLiving.getY() + (double)(entityLiving.getRandom().nextInt(16) - 8), (double)level.getMinBuildHeight(), (double)(level.getMinBuildHeight() + ((ServerLevel)level).getLogicalHeight() - 1));
                double d2 = entityLiving.getZ() + (entityLiving.getRandom().nextDouble() - 0.5) * 16.0;
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
                    SoundSource soundsource;
                    SoundEvent soundevent;
                    if (entityLiving instanceof Fox) {
                        soundevent = SoundEvents.FOX_TELEPORT;
                        soundsource = SoundSource.NEUTRAL;
                    } else {
                        soundevent = SoundEvents.CHORUS_FRUIT_TELEPORT;
                        soundsource = SoundSource.PLAYERS;
                    }

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

        super.onMobHurt(entityLiving, amplifier, damageSource, amount);
    }
}
