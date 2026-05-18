package com.thebeyond.api.worldgen;

import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import org.jetbrains.annotations.ApiStatus;

/** Read-only facade over the server-session End-worldgen state. Use {@link #isActive()}
 *  to gate compat behavior; {@link #getDimMinY()}/{@link #getDimMaxY()} for dim bounds. */
@ApiStatus.Experimental
public final class BeyondTerrainState {
    private BeyondTerrainState() {}

    public static boolean isActive() { return BeyondTerrainStateInternal.active; }
    public static int getDimMinY()   { return BeyondTerrainStateInternal.dimMinY; }
    public static int getDimMaxY()   { return BeyondTerrainStateInternal.dimMaxY; }

    public static DimRange dimRange() {
        return new DimRange(BeyondTerrainStateInternal.dimMinY, BeyondTerrainStateInternal.dimMaxY);
    }

    public record DimRange(int min, int maxExclusive) {}
}
