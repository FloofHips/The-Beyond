package com.thebeyond.common.camera;

import com.thebeyond.common.registry.BeyondComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Reads/writes the filter a camera stamps onto its photos ({@code the_beyond:camera_grade}). The trigger to set it
 * is left to the artist, so an unset camera defaults to {@link Grades#SEPIA}.
 */
public final class CameraGrade {
    private CameraGrade() {
    }

    public static ResourceLocation get(ItemStack camera) {
        ResourceLocation g = camera.get(BeyondComponents.CAMERA_GRADE.get());
        return g != null ? g : Grades.SEPIA;
    }

    public static void set(ItemStack camera, ResourceLocation gradeId) {
        camera.set(BeyondComponents.CAMERA_GRADE.get(), gradeId);
    }
}
