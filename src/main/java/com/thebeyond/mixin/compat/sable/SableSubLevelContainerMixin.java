package com.thebeyond.mixin.compat.sable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Set;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer", remap = false)
public abstract class SableSubLevelContainerMixin {

    @Unique
    private static final ThreadLocal<Boolean> the_beyond$GUARD = ThreadLocal.withInitial(() -> false);

    @Unique
    private static final StackWalker the_beyond$WALKER = StackWalker.getInstance();

    @Unique
    private static final Set<String> the_beyond$CHUNK_TARGETS = Set.of(
            "net.minecraft.server.level.ServerChunkCache",
            "net.minecraft.client.multiplayer.ClientChunkCache",
            "net.minecraft.server.level.ChunkMap"
    );

    @Unique
    private static Method the_beyond$cachedGetPlot;
    @Unique
    private static boolean the_beyond$initFailed = false;

    @Shadow @Final private int logSideLength;
    @Shadow @Final private int logPlotSize;
    @Shadow @Final private int originX;
    @Shadow @Final private int originZ;

    @Inject(method = "inBounds(II)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void the_beyond$safeInBounds(int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (the_beyond$GUARD.get() || the_beyond$initFailed) return;

        // Outside the plotgrid footprint inBounds is false for every caller, so the
        // guard cannot change the result — skip the stack walk (same math as inBounds).
        int plotX = (x >> this.logPlotSize) - this.originX;
        int plotZ = (z >> this.logPlotSize) - this.originZ;
        int side = 1 << this.logSideLength;
        if (plotX < 0 || plotX >= side || plotZ < 0 || plotZ >= side) return;

        if (!the_beyond$isChunkSystem()) return;

        the_beyond$GUARD.set(true);
        try {
            if (the_beyond$cachedGetPlot == null) {
                the_beyond$cachedGetPlot = this.getClass().getMethod("getPlot", int.class, int.class);
            }

            Object plot = the_beyond$cachedGetPlot.invoke(this, x, z);
            if (plot == null) {
                cir.setReturnValue(false);
            }
        } catch (Exception e) {
            the_beyond$initFailed = true;
        } finally {
            the_beyond$GUARD.set(false);
        }
    }

    @Unique
    private static boolean the_beyond$isChunkSystem() {
        return the_beyond$WALKER.walk(frames -> frames
                .limit(10)
                .anyMatch(f -> the_beyond$CHUNK_TARGETS.contains(f.getClassName())));
    }
}
