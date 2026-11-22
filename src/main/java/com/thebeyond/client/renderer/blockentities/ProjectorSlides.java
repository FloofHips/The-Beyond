package com.thebeyond.client.renderer.blockentities;

import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import com.thebeyond.compat.exposure.ExposureCompat;
import net.minecraft.Util;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** What a projector shows: in carousel mode each photograph of a stack or album in turn behind a shutter, elsewhere the first. */
final class ProjectorSlides {
    private static final long SHUTTER_NANOS = 300_000_000L;
    private static final Map<ProjectorBlockEntity, Shutter> SHUTTERS = new WeakHashMap<>();

    private ProjectorSlides() {
    }

    record Slide(ItemStack stack, int index, int count, float opacity) {
    }

    private static final class Shutter {
        int index;
        long changedAt = Util.getNanos() - SHUTTER_NANOS;
        ItemStack shown = ItemStack.EMPTY;
        ItemStack previous = ItemStack.EMPTY;

        Shutter(int index) {
            this.index = index;
        }
    }

    static List<Slide> of(ProjectorBlockEntity be) {
        NonNullList<ItemStack> items = be.getItems();
        int[] filled = be.filledSlots();
        if (be.getMode() != ProjectorBlockEntity.MODE_CAROUSEL) {
            List<Slide> slides = new ArrayList<>(filled.length);
            for (int j = 0; j < filled.length; j++) {
                slides.add(new Slide(ExposureCompat.cover(items.get(filled[j])), j, filled.length, 1f));
            }
            return slides;
        }
        List<ItemStack> tray = new ArrayList<>();
        for (int slot : filled) {
            tray.addAll(ExposureCompat.slides(items.get(slot)));
        }
        if (tray.isEmpty()) {
            return List.of();
        }
        int index = be.getCarouselIndex();
        ItemStack current = tray.get(Math.floorMod(index, tray.size()));
        Shutter shutter = SHUTTERS.computeIfAbsent(be, k -> new Shutter(index));
        long now = Util.getNanos();
        if (shutter.index != index) {
            shutter.index = index;
            shutter.previous = shutter.shown;
            shutter.changedAt = now;
        }
        shutter.shown = current;
        float t = (now - shutter.changedAt) / (float) SHUTTER_NANOS;
        if (t >= 1f || shutter.previous.isEmpty()) {
            return List.of(new Slide(current, 0, 1, 1f));
        }
        return t < 0.5f
                ? List.of(new Slide(shutter.previous, 0, 1, 1f - 2f * t))
                : List.of(new Slide(current, 0, 1, 2f * t - 1f));
    }
}
