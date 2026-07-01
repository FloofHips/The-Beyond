package com.thebeyond.common.registry;

import net.minecraft.client.renderer.ShaderInstance;

import javax.annotation.Nullable;

/** Null until {@code ModClientEvents.onRegisterShaders} populates them. */
public class BeyondShaders {
    private static ShaderInstance ENTITY_DEPTH_SHADER;
    private static ShaderInstance REFUGE_GRADIENT_SHADER;
    private static ShaderInstance MIRROR_SHADER;
    private static ShaderInstance PROJECTOR_GRADE_SEPIA_SHADER;
    private static ShaderInstance PROJECTOR_GRADE_BLUE_SHADER;
    private static ShaderInstance PROJECTOR_DIST_SHADER;
    private static ShaderInstance PROJECTOR_DIST_PEEL_SHADER;
    private static ShaderInstance PROJECTOR_DIST_ENTITY_SHADER;
    private static ShaderInstance PROJECTOR_DECAL_SHADER;

    @Nullable
    public static ShaderInstance getRenderTypeDepthOverlay() {
        return ENTITY_DEPTH_SHADER;
    }

    public static void setRenderTypeDepthOverlay(ShaderInstance instance) {
        ENTITY_DEPTH_SHADER = instance;
    }

    @Nullable
    public static ShaderInstance getRefugeGradient() {
        return REFUGE_GRADIENT_SHADER;
    }

    public static void setRefugeGradient(ShaderInstance instance) {
        REFUGE_GRADIENT_SHADER = instance;
    }

    @Nullable
    public static ShaderInstance getMirror() {
        return MIRROR_SHADER;
    }

    public static void setMirror(ShaderInstance instance) {
        MIRROR_SHADER = instance;
    }

    @Nullable
    public static ShaderInstance getProjectorGradeSepia() {
        return PROJECTOR_GRADE_SEPIA_SHADER;
    }

    public static void setProjectorGradeSepia(ShaderInstance instance) {
        PROJECTOR_GRADE_SEPIA_SHADER = instance;
    }

    @Nullable
    public static ShaderInstance getProjectorGradeBlue() {
        return PROJECTOR_GRADE_BLUE_SHADER;
    }

    public static void setProjectorGradeBlue(ShaderInstance instance) {
        PROJECTOR_GRADE_BLUE_SHADER = instance;
    }

    /** Writes R+G packed radial distance from the projector lens. */
    @Nullable
    public static ShaderInstance getProjectorDist() {
        return PROJECTOR_DIST_SHADER;
    }

    public static void setProjectorDist(ShaderInstance instance) {
        PROJECTOR_DIST_SHADER = instance;
    }

    /** Nearest block surface strictly beyond the first depth layer. */
    @Nullable
    public static ShaderInstance getProjectorDistPeel() {
        return PROJECTOR_DIST_PEEL_SHADER;
    }

    public static void setProjectorDistPeel(ShaderInstance instance) {
        PROJECTOR_DIST_PEEL_SHADER = instance;
    }

    /** Entity depth: R+G radial pack + B=1.0 entity bit; NEW_ENTITY vertex format. */
    @Nullable
    public static ShaderInstance getProjectorDistEntity() {
        return PROJECTOR_DIST_ENTITY_SHADER;
    }

    public static void setProjectorDistEntity(ShaderInstance instance) {
        PROJECTOR_DIST_ENTITY_SHADER = instance;
    }

    @Nullable
    public static ShaderInstance getProjectorDecal() {
        return PROJECTOR_DECAL_SHADER;
    }

    public static void setProjectorDecal(ShaderInstance instance) {
        PROJECTOR_DECAL_SHADER = instance;
    }
}
