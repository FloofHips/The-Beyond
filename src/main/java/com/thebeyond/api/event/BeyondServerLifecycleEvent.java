package com.thebeyond.api.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.ApiStatus;

/** Server-lifecycle event on {@code NeoForge.EVENT_BUS} for addon per-server
 *  bootstrap/teardown, fired in step with Beyond's own state recompute/reset. */
@ApiStatus.Experimental
public abstract class BeyondServerLifecycleEvent extends Event {
    protected final MinecraftServer server;
    protected final boolean beyondTerrainActive;

    protected BeyondServerLifecycleEvent(MinecraftServer server, boolean beyondTerrainActive) {
        this.server = server;
        this.beyondTerrainActive = beyondTerrainActive;
    }

    public MinecraftServer getServer() { return server; }

    /** False in "soup mode" (a foreign pack supplies the_end.json). */
    public boolean isBeyondTerrainActive() { return beyondTerrainActive; }

    /** After Beyond bootstraps server state — addons do biome discovery, surface-rule
     *  merges, macro-region caches here. */
    public static class AboutToStart extends BeyondServerLifecycleEvent {
        public AboutToStart(MinecraftServer server, boolean beyondTerrainActive) {
            super(server, beyondTerrainActive);
        }
    }

    /** After Beyond resets its world-bound state — addons reset their caches here. */
    public static class Stopped extends BeyondServerLifecycleEvent {
        public Stopped(MinecraftServer server, boolean beyondTerrainActive) {
            super(server, beyondTerrainActive);
        }
    }
}
