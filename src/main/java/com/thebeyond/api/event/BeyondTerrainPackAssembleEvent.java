package com.thebeyond.api.event;

import net.minecraft.server.packs.repository.Pack;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Mod-bus event in AddPackFindersEvent where addons submit a height-bounds pack, only the highest priority attaches. */
@ApiStatus.Experimental
public class BeyondTerrainPackAssembleEvent extends Event implements IModBusEvent {
    /** A single bounds-override proposal. */
    public record Contribution(String packName, int priority, Pack pack, String logMessage) {}

    private final AddPackFindersEvent neoForgeEvent;
    private final List<Contribution> contributions = new ArrayList<>();

    public BeyondTerrainPackAssembleEvent(AddPackFindersEvent neoForgeEvent) {
        this.neoForgeEvent = neoForgeEvent;
    }

    public AddPackFindersEvent getNeoForgeEvent() { return neoForgeEvent; }

    /** Submits a height-bounds child pack, the higher priority wins and a tie goes to the first registered. */
    public void contributeBoundsOverride(String packName, int priority, Pack pack, String logMessage) {
        contributions.add(new Contribution(packName, priority, pack, logMessage));
    }

    /** All contributions submitted so far (read-only view). */
    public List<Contribution> getContributions() {
        return Collections.unmodifiableList(contributions);
    }

    /** Highest-priority contribution, or {@code null} if none was submitted. */
    @Nullable
    public Contribution resolveWinner() {
        return contributions.stream()
                .max(Comparator.comparingInt(Contribution::priority))
                .orElse(null);
    }
}
