package com.thebeyond.client.compat;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.specialeffects.EndSpecialEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/** Warns once when another mod computes the End fog color outside setupColor or redoes vanilla's night vision math on it. */
@EventBusSubscriber(modid = TheBeyond.MODID, value = Dist.CLIENT)
public final class NightVisionFogGuard {
    private static final int SKIPPED_FRAMES = 40;
    private static final int FLIPPED_CALLS = 20;

    private static boolean setupColorRan;
    private static int skippedFrames;
    private static boolean warnedSkipped;

    private static boolean haveFixed;
    private static float fixedRed;
    private static float fixedGreen;
    private static float fixedBlue;
    private static int flippedCalls;
    private static boolean warnedFlipped;

    private NightVisionFogGuard() {
    }

    public static void afterNightVision(float red, float green, float blue) {
        fixedRed = red;
        fixedGreen = green;
        fixedBlue = blue;
        haveFixed = true;
    }

    public static void setupColorEnded(float red, float green, float blue) {
        setupColorRan = true;
        if (!haveFixed) {
            return;
        }
        haveFixed = false;
        if (warnedFlipped) {
            return;
        }
        // vanilla's math with a negative factor turns negative channels positive and positive ones negative, or 0 after a clamp
        boolean mixed = (fixedRed < 0 || fixedGreen < 0 || fixedBlue < 0) && (fixedRed > 0 || fixedGreen > 0 || fixedBlue > 0);
        if (mixed && flipped(fixedRed, red) && flipped(fixedGreen, green) && flipped(fixedBlue, blue)) {
            if (++flippedCalls >= FLIPPED_CALLS) {
                warnedFlipped = true;
                TheBeyond.LOGGER.warn("[TheBeyond] Another mod turned the End fog green again after FogRenderer.setupColor kept its sign "
                        + "(a ViewportEvent.ComputeFogColor listener or a FogRenderer mixin redoing vanilla's night vision math). "
                        + "Mixins on FogRenderer: {}", mixinsOnFogRenderer());
            }
        } else {
            flippedCalls = 0;
        }
    }

    @SubscribeEvent
    public static void afterSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        boolean ran = setupColorRan;
        setupColorRan = false;
        Minecraft mc = Minecraft.getInstance();
        if (warnedSkipped || mc.level == null || !(mc.level.effects() instanceof EndSpecialEffects)) {
            skippedFrames = 0;
            return;
        }
        skippedFrames = ran ? 0 : skippedFrames + 1;
        if (skippedFrames >= SKIPPED_FRAMES) {
            warnedSkipped = true;
            TheBeyond.LOGGER.warn("[TheBeyond] FogRenderer.setupColor did not run for {} frames in the End, so another mod computes the fog "
                    + "color and night vision can turn the sky green. Mixins on FogRenderer: {}", SKIPPED_FRAMES, mixinsOnFogRenderer());
        }
    }

    private static boolean flipped(float fixed, float now) {
        return fixed < 0 ? now > 0 : fixed == 0 || now <= 0;
    }

    private static Set<String> mixinsOnFogRenderer() {
        Set<String> owners = new TreeSet<>();
        for (Method m : FogRenderer.class.getDeclaredMethods()) {
            MixinMerged merged = m.getAnnotation(MixinMerged.class);
            if (merged != null) {
                owners.add(merged.mixin());
            }
        }
        return owners;
    }
}
