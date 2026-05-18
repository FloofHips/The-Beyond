package com.thebeyond.compat.sable;

import com.thebeyond.TheBeyond;
import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.api.compat.LocationTranslator;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Registers Sable's stored→visible projection. Loaded only after {@code isLoaded("sable")}. */
public final class BeyondSableCompat {
    private BeyondSableCompat() {}

    public static void register() {
        try {
            BeyondCompatHooks.locationTranslator = new SableTranslator();
            TheBeyond.LOGGER.info("[TheBeyond] Sable LocationTranslator registered.");
        } catch (Throwable t) {
            TheBeyond.LOGGER.error("[TheBeyond] Failed to register Sable LocationTranslator", t);
        }
    }

    private static final class SableTranslator implements LocationTranslator {
        @Override
        public Vec3 toVisible(ServerLevel level, BlockPos storedPos) {
            SubLevel sub = Sable.HELPER.getContaining(level, storedPos);
            if (sub == null) return null;
            return Sable.HELPER.projectOutOfSubLevel(level, storedPos.getCenter());
        }

        @Override
        public BlockPos toStored(ServerLevel level, Vec3 visiblePos) {
            BoundingBox3d bounds = new BoundingBox3d(BlockPos.containing(visiblePos));
            Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(level, bounds);
            for (SubLevel sub : intersecting) {
                Vector3d v = new Vector3d(visiblePos.x, visiblePos.y, visiblePos.z);
                sub.logicalPose().transformPositionInverse(v);
                return BlockPos.containing(v.x, v.y, v.z);
            }
            return null;
        }
    }
}
