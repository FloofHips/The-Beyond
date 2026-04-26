package com.thebeyond.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * Accessor for {@link BossHealthOverlay#events} so Beyond can check whether any boss
 * bar is visible — regardless of its Java-side flags ({@code createWorldFog},
 * {@code darkenScreen}, etc.).
 *
 * <p>Stellarity uses command boss bars ({@code /bossbar add}) which do NOT set the
 * {@code createWorldFog} flag. Without this accessor, {@code shouldCreateWorldFog()}
 * returns {@code false} during Stellarity's dragon fight, causing the 3D cloud rings
 * to not render.</p>
 */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {

    @Accessor("events")
    Map<UUID, LerpingBossEvent> the_beyond$getEvents();
}
