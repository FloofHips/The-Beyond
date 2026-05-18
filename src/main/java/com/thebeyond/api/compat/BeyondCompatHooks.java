package com.thebeyond.api.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Static hook surface used by BlockEntity ticks to query compat-side translators
 *  without referencing third-party types directly. */
@ApiStatus.Experimental
public final class BeyondCompatHooks {
    private BeyondCompatHooks() {}

    public static volatile LocationTranslator locationTranslator;

    /** Visible Vec3 if {@code storedPos} is inside a sub-level, otherwise the block center. */
    public static Vec3 visibleOrCenter(Level level, BlockPos storedPos) {
        if (level instanceof ServerLevel sl) {
            LocationTranslator t = locationTranslator;
            if (t != null) {
                Vec3 v = t.toVisible(sl, storedPos);
                if (v != null) return v;
            }
        }
        return storedPos.getCenter();
    }

    /** Null-returning variant for callers that need to know whether a translation happened. */
    @Nullable
    public static Vec3 visibleOnly(Level level, BlockPos storedPos) {
        if (!(level instanceof ServerLevel sl)) return null;
        LocationTranslator t = locationTranslator;
        return t == null ? null : t.toVisible(sl, storedPos);
    }

    /** Reverse: visible-world position → stored BlockPos (null if not inside any sub-level). */
    @Nullable
    public static BlockPos storedForVisible(Level level, Vec3 visiblePos) {
        if (!(level instanceof ServerLevel sl)) return null;
        LocationTranslator t = locationTranslator;
        return t == null ? null : t.toStored(sl, visiblePos);
    }

    @Nullable
    public static BlockPos storedForVisible(Level level, BlockPos visiblePos) {
        return storedForVisible(level, visiblePos.getCenter());
    }
}
