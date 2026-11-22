package com.thebeyond.compat.exposure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.client.renderer.RenderFailureLog;
import com.thebeyond.compat.exposure.ExposureCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ExposureClientCompat {
    private static final List<CompletableFuture<?>> DIRECT_SHOTS = new ArrayList<>();

    private ExposureClientCompat() {
    }

    /** True while Exposure renders the world for a photograph, or while a direct capture waits for its frame. */
    public static boolean isCapturing() {
        if (!ExposureCompat.LOADED) {
            return false;
        }
        DIRECT_SHOTS.removeIf(CompletableFuture::isDone);
        if (!DIRECT_SHOTS.isEmpty()) {
            return true;
        }
        try {
            return ExposureClientHooks.capturing();
        } catch (LinkageError e) {
            RenderFailureLog.error("[Beyond] Exposure capture state unreadable, imageless entities show in its photos", e);
            return false;
        }
    }

    public static void trackDirectCapture(CompletableFuture<?> shot) {
        if (shot != null && !shot.isDone()) {
            DIRECT_SHOTS.add(shot);
        }
    }

    public static boolean isPhotograph(ItemStack stack) {
        try {
            return ExposureCompat.LOADED && ExposureClientHooks.isPhotograph(stack);
        } catch (LinkageError e) {
            RenderFailureLog.error("[Beyond] Exposure photographs unreadable, the projector shows their icon", e);
            return false;
        }
    }

    /** Draws a photograph's image without its paper over the unit square, false while the exposure is still loading. */
    public static boolean renderPhotograph(ItemStack stack, PoseStack pose, MultiBufferSource buffers) {
        try {
            return ExposureCompat.LOADED && ExposureClientHooks.renderPhotograph(stack, pose, buffers);
        } catch (LinkageError e) {
            RenderFailureLog.error("[Beyond] Exposure photograph could not be drawn for the projector", e);
            return false;
        }
    }
}
