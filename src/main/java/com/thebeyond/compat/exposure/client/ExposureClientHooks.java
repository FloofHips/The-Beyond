package com.thebeyond.compat.exposure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.client.capture.task.BackgroundScreenshotCaptureTask;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

final class ExposureClientHooks {
    private ExposureClientHooks() {
    }

    static boolean capturing() {
        return BackgroundScreenshotCaptureTask.isCapturing();
    }

    static boolean isPhotograph(ItemStack stack) {
        return stack.getItem() instanceof PhotographItem;
    }

    static boolean renderPhotograph(ItemStack stack, PoseStack pose, MultiBufferSource buffers) {
        if (!(stack.getItem() instanceof PhotographItem photograph)) {
            return false;
        }
        return ExposureClient.photographRenderer().renderPhotograph(pose, buffers, photograph, stack, false, false,
                LightTexture.FULL_BRIGHT, 255, 255, 255, 255);
    }
}
