package com.thebeyond.common.effect;

import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.util.TeleportUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class NomadsBlessingEffect extends GenericEffect {
    public NomadsBlessingEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        boolean inEnd = livingEntity.level().dimension().equals(Level.END);
        if (!inEnd) return super.applyEffectTick(livingEntity, amplifier);

        if (livingEntity.position().y < livingEntity.level().getMinBuildHeight() - 5) {
            Level level = livingEntity.level();
            TeleportUtils.randomTeleport(level, livingEntity);

            if (livingEntity instanceof Player player) {
                ModClientEvents.nomadEyes = 1;
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL);
            }

            return super.applyEffectTick(livingEntity, amplifier);
        }

        return super.applyEffectTick(livingEntity, amplifier);
    }
}
