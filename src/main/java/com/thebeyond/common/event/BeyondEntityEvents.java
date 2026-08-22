package com.thebeyond.common.event;

import com.thebeyond.BeyondConfig;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.AuroraciteBlock;
import com.thebeyond.common.deafening.Deafening;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = TheBeyond.MODID)
public class BeyondEntityEvents {

    @SubscribeEvent
    public static void onChorusFruitTeleport(EntityTeleportEvent.ChorusFruit event) {
        Entity entity = event.getEntity();
        if (AuroraciteBlock.canEntityWalkOn(entity)) return;

        Level level = entity.level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
                event.getTargetX(), event.getTargetY(), event.getTargetZ());
        int min = level.getMinBuildHeight();

        while (cursor.getY() > min) {
            BlockState below = level.getBlockState(cursor.below());
            if (below.blocksMotion()) {
                if (below.is(BeyondBlocks.AURORACITE.get())) {
                    event.setCanceled(true);
                }
                return;
            }
            cursor.move(0, -1, 0);
        }
    }

    /**
     * Deafening FOV-stealth: gates a deafened mob's player-acquisition. Hooks {@code Mob.setTarget} (goals + Species'
     * Ghoul, whose vestigial VibrationSystem makes this its real hook) and {@code StartAttacking}; not the Warden (its mixins).
     */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide) return; // target acquisition is server-authoritative; skip the client copy
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player)) return;
        if (Deafening.isImmune(mob)) return;              // always notices
        if (!Deafening.isDeafened(mob)) return;           // not deaf → normal targeting
        if (mob.getLastHurtByMob() == player) return;     // retaliation — being attacked always aggros

        boolean nonVisual = Deafening.sensesViaVibration(mob);
        if (!nonVisual && Deafening.canSeeInFov(mob, player)) return; // sighted: player within cone + LOS → noticed

        event.setCanceled(true);
        if (mob.tickCount % 20 == 0) { // throttle: ~1/s per suppressed mob confirms the rule is firing
            TheBeyond.LOGGER.debug("[Deafening] {} kept from targeting player (deaf; {})",
                    mob.getType().getDescriptionId(), nonVisual ? "non-visual hunter" : "outside FOV cone/LOS");
        }
    }

    /**
     * Disengage on cone-exit: a deafened mob DROPS an acquired player target when the player leaves its live frontal cone/LOS (re-evaluated with body yaw, so it turns with the mob).
     * Checked every 10 ticks; non-visual ({@code senses_via_vibration}), immune, and just-hit mobs are exempt; re-acquisition is then blocked by {@link #onLivingChangeTarget}.
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;
        Deafening.tickStartle(mob); // post-AI so the stare wins over look goals; O(1) when no startle is active
        if (mob.tickCount % 10 != 0) return;
        if (!(mob.getTarget() instanceof Player player)) return;
        if (!BeyondConfig.DEAFENING_DISENGAGE.get()) return;
        if (Deafening.isImmune(mob) || Deafening.sensesViaVibration(mob)) return;
        if (!Deafening.isDeafened(mob)) return;
        if (mob.getLastHurtByMob() == player) return; // don't drop someone who just hit us
        if (Deafening.canSeeInFov(mob, player)) return;

        mob.setTarget(null);                                        // goal-based mobs (fires the change event with null — ignored above)
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET); // brain-based mobs; no-op when the module is absent
        TheBeyond.LOGGER.debug("[Deafening] {} dropped player target (deaf + player left FOV cone/LOS)",
                mob.getType().getDescriptionId());
    }

    @SubscribeEvent
    public static void onEntityHurt(LivingDamageEvent.Post event) {
        Entity entity = event.getSource().getEntity();
        if (entity == null) return;

        if (entity instanceof LivingEntity livingEntity && !livingEntity.hasEffect(BeyondEffects.EMPATHY)) return;
        entity.hurt(event.getSource(), event.getNewDamage());
    }
}
