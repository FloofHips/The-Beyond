package com.thebeyond.api.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Bridges stored ↔ visible coordinates for blocks inside a virtual contraption/sub-level
 *  (e.g. Sable plot). Registered in {@link BeyondCompatHooks} by compat modules at mod-load. */
@ApiStatus.Experimental
public interface LocationTranslator {
    /** Stored BE pos → visible Vec3 (or {@code null} when not in a sub-level). */
    @Nullable Vec3 toVisible(ServerLevel level, BlockPos storedPos);

    /** Visible Vec3 → stored BlockPos (or {@code null} when not inside any sub-level). */
    @Nullable BlockPos toStored(ServerLevel level, Vec3 visiblePos);

    /** Stored pos → visible/world center on any Level (client-safe); {@code null} when not in a sub-level. */
    @Nullable default Vec3 toVisibleAny(Level level, BlockPos storedPos) { return null; }

    /** World point → the local (stored) frame of the sub-level containing {@code containedPos};
     *  {@code null} when not in a sub-level. */
    @Nullable default Vec3 toLocal(Level level, BlockPos containedPos, Vec3 worldPoint) { return null; }
}
