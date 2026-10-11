package com.thebeyond.api.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Converts stored and visible coordinates of blocks inside a sub-level (a Sable plot), registered by compat modules. */
@ApiStatus.Experimental
public interface LocationTranslator {
    /** Stored BE pos → visible Vec3 (or {@code null} when not in a sub-level). */
    @Nullable Vec3 toVisible(ServerLevel level, BlockPos storedPos);

    /** Visible Vec3 → stored BlockPos (or {@code null} when not inside any sub-level). */
    @Nullable BlockPos toStored(ServerLevel level, Vec3 visiblePos);

    /** Stored pos to world center on any Level (client-safe), null outside a sub-level. */
    @Nullable default Vec3 toVisibleAny(Level level, BlockPos storedPos) { return null; }

    /** World point to the stored frame of the sub-level holding containedPos, null outside a sub-level. */
    @Nullable default Vec3 toLocal(Level level, BlockPos containedPos, Vec3 worldPoint) { return null; }

    /** A local direction at containedPos turned into world space, null outside a sub-level so the caller keeps it. */
    @Nullable default Vec3 toVisibleDir(Level level, BlockPos containedPos, Vec3 localDir) { return null; }
}
