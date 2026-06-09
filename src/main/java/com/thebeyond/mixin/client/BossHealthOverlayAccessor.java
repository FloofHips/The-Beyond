package com.thebeyond.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** Exposes {@link BossHealthOverlay#events} so Beyond can detect any boss bar regardless
 *  of {@code createWorldFog}/{@code darkenScreen} flags — Stellarity's {@code /bossbar add}
 *  bars don't set those, so the vanilla check misses the dragon fight. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {

    @Accessor("events")
    Map<UUID, LerpingBossEvent> the_beyond$getEvents();
}
