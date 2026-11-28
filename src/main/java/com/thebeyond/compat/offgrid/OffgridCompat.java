package com.thebeyond.compat.offgrid;

import com.thebeyond.TheBeyond;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/** Offgrid keeps blocks placed inside another block's cell as overlays, which the level's block grid does not show. */
public final class OffgridCompat {
    private static final MethodHandle OVERLAYS;
    private static final MethodHandle STATE;
    private static volatile boolean broken;

    static {
        MethodHandle overlays = null;
        MethodHandle state = null;
        if (ModList.get().isLoaded("offgrid")) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                overlays = lookup.findStatic(Class.forName("dev.tazer.offgrid.world.Cells"), "overlays",
                        MethodType.methodType(List.class, BlockGetter.class, BlockPos.class));
                state = lookup.findVirtual(Class.forName("dev.tazer.offgrid.world.Overlay"), "getState",
                        MethodType.methodType(BlockState.class));
            } catch (ReflectiveOperationException e) {
                TheBeyond.LOGGER.warn("[Beyond] Offgrid is loaded but its overlays did not resolve, projectors ignore lights placed inside other blocks", e);
                overlays = null;
            }
        }
        OVERLAYS = overlays;
        STATE = state;
    }

    private OffgridCompat() {
    }

    /** Brightest light emitted by the Offgrid overlays in this cell, 0 without Offgrid. */
    public static int overlayLight(BlockGetter level, BlockPos pos) {
        if (OVERLAYS == null || broken) {
            return 0;
        }
        try {
            int light = 0;
            for (Object overlay : (List<?>) OVERLAYS.invoke(level, pos)) {
                light = Math.max(light, ((BlockState) STATE.invoke(overlay)).getLightEmission(level, pos));
            }
            return light;
        } catch (Throwable t) {
            broken = true;
            TheBeyond.LOGGER.warn("[Beyond] Offgrid overlays became unreadable, projectors ignore lights placed inside other blocks", t);
            return 0;
        }
    }
}
