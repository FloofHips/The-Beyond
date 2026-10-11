package com.thebeyond.common.registry;

import net.minecraft.client.renderer.ShaderInstance;

import javax.annotation.Nullable;

/** Null until {@code ModClientEvents.onRegisterShaders} populates them. */
public class BeyondShaders {
    private static ShaderInstance ENTITY_DEPTH_SHADER;
    private static ShaderInstance REFUGE_GRADIENT_SHADER;
    private static ShaderInstance MIRROR_SHADER;
    private static ShaderInstance PROJECTOR_GRADE_DATA_SHADER;
    private static ShaderInstance PROJECTOR_DIST_SHADER;
    private static ShaderInstance PROJECTOR_DIST_PEEL_SHADER;
    private static ShaderInstance PROJECTOR_DIST_ENTITY_SHADER;
    private static ShaderInstance PROJECTOR_DIST_BLOCK_ENTITY_SHADER;
    private static ShaderInstance PROJECTOR_DIST_BLOCK_ENTITY_PEEL_SHADER;
    private static ShaderInstance PROJECTOR_DECAL_SHADER;
    @Nullable
    private static ShaderInstance PROJECTOR_DEPTH_MERGE_SHADER;

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

    /** Data-driven item-icon grade: ramp LUT (Sampler1) blended at a Strength uniform. */
    @Nullable
    public static ShaderInstance getProjectorGradeData() {
        return PROJECTOR_GRADE_DATA_SHADER;
    }

    public static void setProjectorGradeData(ShaderInstance instance) {
        PROJECTOR_GRADE_DATA_SHADER = instance;
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

    /** Entity depth: radial distance in R and G, entity bit in B, NEW_ENTITY vertex format. */
    @Nullable
    public static ShaderInstance getProjectorDistEntity() {
        return PROJECTOR_DIST_ENTITY_SHADER;
    }

    public static void setProjectorDistEntity(ShaderInstance instance) {
        PROJECTOR_DIST_ENTITY_SHADER = instance;
    }

    /** Block entity depth: the block encoding (entity bit off, blocks-only distance in B) from NEW_ENTITY vertices. */
    @Nullable
    public static ShaderInstance getProjectorDistBlockEntity() {
        return PROJECTOR_DIST_BLOCK_ENTITY_SHADER;
    }

    public static void setProjectorDistBlockEntity(ShaderInstance instance) {
        PROJECTOR_DIST_BLOCK_ENTITY_SHADER = instance;
    }

    @Nullable
    public static ShaderInstance getProjectorDistBlockEntityPeel() {
        return PROJECTOR_DIST_BLOCK_ENTITY_PEEL_SHADER;
    }

    public static void setProjectorDistBlockEntityPeel(ShaderInstance instance) {
        PROJECTOR_DIST_BLOCK_ENTITY_PEEL_SHADER = instance;
    }

    @Nullable
    public static ShaderInstance getProjectorDecal() {
        return PROJECTOR_DECAL_SHADER;
    }

    public static void setProjectorDecal(ShaderInstance instance) {
        PROJECTOR_DECAL_SHADER = instance;
    }

    @Nullable
    public static ShaderInstance getProjectorDepthMerge() {
        return PROJECTOR_DEPTH_MERGE_SHADER;
    }

    public static void setProjectorDepthMerge(ShaderInstance instance) {
        PROJECTOR_DEPTH_MERGE_SHADER = instance;
    }
}
