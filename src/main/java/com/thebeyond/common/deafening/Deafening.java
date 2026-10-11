package com.thebeyond.common.deafening;

import com.thebeyond.BeyondConfig;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.util.FovStealth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** A deafened mob notices the player only inside its frontal cone with line of sight, or through vibrations. */
public final class Deafening {
    private Deafening() {}

    public static boolean isImmune(Entity e) {
        return e.getType().is(BeyondTags.IMMUNE_TO_DEAFENING);
    }

    public static boolean sensesViaVibration(Entity e) {
        return e.getType().is(BeyondTags.SENSES_VIA_VIBRATION);
    }

    public static boolean isDeafened(LivingEntity e) {
        return e.hasEffect(BeyondEffects.DEAFENED);
    }

    /** No particles, the feedback is the screech and the startle. */
    public static void deafen(LivingEntity target, int durationTicks) {
        // ambient=false, visible(particles)=false, showIcon=true
        target.addEffect(new MobEffectInstance(BeyondEffects.DEAFENED, durationTicks, 0, false, false, true));
    }

    /** Past the crowd cap nobody is deafened, a crowd notices you anyway. */
    public static int deafenMobsAround(ServerLevel level, Vec3 center, double radius, int durationTicks) {
        // Use the smaller cap so a globalCap set below localCap still governs.
        int cap = Math.min(BeyondConfig.DEAFENING_LOCAL_CAP.get(), BeyondConfig.DEAFENING_GLOBAL_CAP.get());
        AABB box = new AABB(center, center).inflate(radius);
        List<Mob> eligible = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && !isImmune(m));

        if (eligible.size() > cap) {
            // Cap trip: the whole burst deafens nobody.
            TheBeyond.LOGGER.debug("[Deafening] burst at {} deafened 0: {} eligible mobs exceeds cap {}",
                    center, eligible.size(), cap);
            return 0;
        }
        for (Mob m : eligible) {
            deafen(m, durationTicks);
        }
        return eligible.size();
    }

    /** Ignores the crowd cap, which balances mob stealth only, a screech deafens players regardless. */
    public static int deafenPlayersAround(ServerLevel level, Vec3 center, double radius, int durationTicks) {
        List<Player> players = level.getEntitiesOfClass(Player.class,
                new AABB(center, center).inflate(radius), p -> p.isAlive() && !p.isSpectator());
        for (Player p : players) {
            deafen(p, durationTicks);
        }
        return players.size();
    }

    /** Server thread only, weak keys drop despawned mobs. */
    private static final Map<Mob, Startle> STARTLES = new WeakHashMap<>();
    private static final int STARTLE_TICKS = 40;            // hold the stare ~2s
    private static final float STARTLE_TURN_DEG_PER_TICK = 40.0F; // fast flinch: ~150° in 4 ticks

    private record Startle(Vec3 point, int untilTick) {}

    /** Driven by tickStartle. Call it before the deafen pass, deaf mobs are left out here. */
    public static void startleMobsAround(ServerLevel level, Vec3 center, double radius) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius),
                m -> m.isAlive() && !isImmune(m) && !sensesViaVibration(m) && !isDeafened(m))) {
            STARTLES.put(m, new Startle(center, m.tickCount + STARTLE_TICKS));
        }
    }

    /** Runs after the mob's AI tick so the stare wins over look goals, a combat target cancels it. */
    public static void tickStartle(Mob mob) {
        if (STARTLES.isEmpty()) return;
        Startle s = STARTLES.get(mob);
        if (s == null) return;
        if (mob.tickCount > s.untilTick() || !mob.isAlive() || mob.getTarget() != null) {
            STARTLES.remove(mob);
            return;
        }
        double dx = s.point().x - mob.getX();
        double dz = s.point().z - mob.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        if (hDist < 1.0e-2) return;  // burst at the mob itself, nothing to turn toward

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float headYaw = Mth.approachDegrees(mob.getYHeadRot(), targetYaw, STARTLE_TURN_DEG_PER_TICK);
        mob.setYHeadRot(headYaw);
        mob.setYBodyRot(Mth.approachDegrees(mob.yBodyRot, targetYaw, STARTLE_TURN_DEG_PER_TICK * 0.5F));

        double dy = s.point().y - mob.getEyeY();
        float targetPitch = (float) -(Mth.atan2(dy, hDist) * (180.0 / Math.PI));
        mob.setXRot(Mth.approachDegrees(mob.getXRot(), Mth.clamp(targetPitch, -40.0F, 40.0F), STARTLE_TURN_DEG_PER_TICK));
    }

    /** The cone follows the head, not the body, so it tilts with the gaze, and the raycast runs only after the cone test. */
    public static boolean canSeeInFov(Mob mob, LivingEntity target) {
        if (!FovStealth.inFovCone(mob.getEyePosition(), target.getEyePosition(), mob.getYHeadRot(), mob.getXRot())) {
            return false;
        }
        return mob.getSensing().hasLineOfSight(target);
    }

    /** The warden stays deaf but investigates and angers at the thrower, acting only within smell range. */
    public static void alertWardensAround(ServerLevel level, Vec3 center, double radius, @Nullable LivingEntity thrower) {
        int anger = BeyondConfig.WARDEN_ENRAGE_ANGER.get();
        BlockPos burstPos = BlockPos.containing(center);
        for (Warden warden : level.getEntitiesOfClass(Warden.class, new AABB(center, center).inflate(radius))) {
            WardenAi.setDisturbanceLocation(warden, burstPos);
            if (thrower != null && warden.canTargetEntity(thrower)) {
                warden.increaseAngerAt(thrower, anger, true);
            }
        }
    }
}
