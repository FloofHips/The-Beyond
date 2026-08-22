package com.thebeyond.common.effect;

import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class EmpathyEffect extends MobEffect {
    public EmpathyEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void onEffectAdded(LivingEntity livingEntity, int amplifier) {

        if (livingEntity instanceof Player player) {
            //if (player == Minecraft.getInstance().player) {
                ModClientEvents.empathy = 1;
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(), BeyondSoundEvents.ABYSSAL_NOMAD_NOD.get(), SoundSource.NEUTRAL);
            //}
        }

        super.onEffectAdded(livingEntity, amplifier);
    }
}
