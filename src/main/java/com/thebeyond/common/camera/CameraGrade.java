package com.thebeyond.common.camera;

import com.thebeyond.common.registry.BeyondComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The filter a camera stamps on its photos, SEPIA while unset since setting it is left to the artist. */
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
