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

/** Mod-bus event during {@code AddPackFindersEvent}: addons submit a bounds-override child
 *  pack; they can't coexist, so only the highest-{@code priority} one is attached. */
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

    /** Submit a bounds-override child pack. Higher {@code priority} wins on conflict;
     *  ties are resolved in registration order (first wins). */
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
