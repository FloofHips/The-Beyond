package com.thebeyond.common.registry;

import net.minecraft.client.renderer.ShaderInstance;

import javax.annotation.Nullable;

/** Live shader instances populated by {@code ModClientEvents.onRegisterShaders}. */
public class BeyondShaders {
    private static ShaderInstance ENTITY_DEPTH_SHADER;
    private static ShaderInstance REFUGE_GRADIENT_SHADER;

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
}
