package com.thebeyond.common.effect;

import com.thebeyond.util.TeleportUtils;
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
        TeleportUtils.randomTeleport(level, entityLiving);
        super.onMobHurt(entityLiving, amplifier, damageSource, amount);
    }
}
