package com.thebeyond.api.event;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

/** Fires during End biome auto-discovery; subscribers add biome holders the tag scan
 *  can't see (e.g. Fabric-port mods exposing End biomes via static fields). */
@ApiStatus.Experimental
public class BeyondBiomeDiscoveryEvent extends Event {
    private final MinecraftServer server;
    private final Registry<Biome> biomeRegistry;
    private final Map<ResourceKey<Biome>, Holder<Biome>> candidates;

    public BeyondBiomeDiscoveryEvent(MinecraftServer server,
                                     Registry<Biome> biomeRegistry,
                                     Map<ResourceKey<Biome>, Holder<Biome>> candidates) {
        this.server = server;
        this.biomeRegistry = biomeRegistry;
        this.candidates = candidates;
    }

    public MinecraftServer getServer() { return server; }
    public Registry<Biome> getBiomeRegistry() { return biomeRegistry; }

    /** Adds {@code holder} to the candidate pool; no-op if unbound or its
     *  {@link ResourceKey} is already taken. @return {@code true} if newly registered. */
    public boolean contribute(Holder<Biome> holder) {
        return holder.unwrapKey()
                .map(key -> candidates.putIfAbsent(key, holder) == null)
                .orElse(false);
    }
}
