package com.thebeyond.api.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
}
