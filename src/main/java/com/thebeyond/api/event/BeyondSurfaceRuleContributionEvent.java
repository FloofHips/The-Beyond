package com.thebeyond.api.event;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fires during {@code SurfaceRuleMerger.mergeSurfaceRules}; contributed rules land before
 *  foreign whole-settings blobs whose terminators would otherwise short-circuit them. */
@ApiStatus.Experimental
public class BeyondSurfaceRuleContributionEvent extends Event {
    private final MinecraftServer server;
    private final RegistryAccess registryAccess;
    private final BiomeSource endBiomeSource;
    private final List<SurfaceRules.RuleSource> contributions = new ArrayList<>();

    public BeyondSurfaceRuleContributionEvent(MinecraftServer server,
                                              RegistryAccess registryAccess,
                                              BiomeSource endBiomeSource) {
        this.server = server;
        this.registryAccess = registryAccess;
        this.endBiomeSource = endBiomeSource;
    }

    public MinecraftServer getServer() { return server; }
    public RegistryAccess getRegistryAccess() { return registryAccess; }
    public BiomeSource getEndBiomeSource() { return endBiomeSource; }

    /** Adds a rule to the merge sequence. Caller is responsible for wrapping in
     *  {@code SurfaceRules.ifTrue(SurfaceRules.isBiome(...), …)} when biome-specific. */
    public void contribute(SurfaceRules.RuleSource rule) {
        contributions.add(rule);
    }

    public List<SurfaceRules.RuleSource> getContributions() {
        return Collections.unmodifiableList(contributions);
    }
}
