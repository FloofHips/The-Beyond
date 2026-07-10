package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.deafening.Deafening;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondPotions;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/** Brewing recipe + on-break behavior for the deafening potion, which routes all its actual
 *  effects through {@link Deafening} so the crowd cap and mob-only filtering stay centralized. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public class BeyondDeafeningPotionEvents {

    private static final double SPLASH_RADIUS = 6.0;
    private static final double LINGER_RADIUS = 8.0;
    /** Public: the client tooltip renders these durations (single source of truth). */
    public static final int SPLASH_DURATION = 20 * 15;    // 15s
    public static final int LINGER_DURATION = 20 * 40;    // 40s

    @SubscribeEvent
    public static void onRegisterBrewing(RegisterBrewingRecipesEvent event) {
        // One mix auto-covers drinkable + splash + lingering; vanilla adds the gunpowder/dragon-breath conversions.
        // Obiroot sprout = the plant the Enadrakes (the screech source) harvest and replant.
        event.getBuilder().addMix(Potions.AWKWARD, BeyondBlocks.OBIROOT_SPROUT.asItem(), BeyondPotions.DEAFENING);
        TheBeyond.LOGGER.debug("[Deafening] registered brewing mix: awkward + obiroot_sprout -> deafening");
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion)) return;
        if (!(potion.level() instanceof ServerLevel level)) return;

        PotionContents contents = potion.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        boolean isDeafening = contents.potion().map(h -> h.is(BeyondPotions.DEAFENING.getKey())).orElse(false);
        if (!isDeafening) return;

        Vec3 pos = event.getRayTraceResult().getLocation();
        boolean lingering = potion.getItem().is(Items.LINGERING_POTION);
        double radius = lingering ? LINGER_RADIUS : SPLASH_RADIUS;
        int duration = lingering ? LINGER_DURATION : SPLASH_DURATION;

        level.playSound(null, pos.x, pos.y, pos.z, BeyondSoundEvents.ENADRAKE_SCREECH.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        // Startle BEFORE deafening: only mobs that could still hear this burst whip toward it; mobs already
        // deaf from an earlier burst are skipped (the deafen below still refreshes their duration).
        Deafening.startleMobsAround(level, pos, radius + 2.0);
        int deafened = Deafening.deafenMobsAround(level, pos, radius, duration);
        // Symmetric model: players in radius (thrower included) go deaf too — client audio-mute.
        int deafenedPlayers = Deafening.deafenPlayersAround(level, pos, radius, duration);
        LivingEntity thrower = potion.getOwner() instanceof LivingEntity le ? le : null;
        Deafening.alertWardensAround(level, pos, radius, thrower);

        TheBeyond.LOGGER.debug("[Deafening] potion broke at {} ({}): deafened {} mob(s), {} player(s)",
                pos, lingering ? "lingering" : "splash", deafened, deafenedPlayers);
        // Do NOT cancel: canceling ProjectileImpactEvent makes the potion keep flying instead of breaking.
    }
}
