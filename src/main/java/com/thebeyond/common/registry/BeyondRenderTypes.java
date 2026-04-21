package com.thebeyond.common.registry;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.ClientHooks;

import java.util.function.Function;

public class BeyondRenderTypes extends RenderType {
    public BeyondRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
    private static final RenderStateShard.ShaderStateShard RENDERTYPE_ENTITY_TRANSLUCENT_UNLIT_SHADER = new RenderStateShard.ShaderStateShard(ClientHooks.ClientEvents::getEntityTranslucentUnlitShader);
    static RenderStateShard.ShaderStateShard DEPTH_SHADER_STATE = new RenderStateShard.ShaderStateShard(BeyondShaders::getRenderTypeDepthOverlay);
    static RenderStateShard.ShaderStateShard REFUGE_GRADIENT_SHADER_STATE = new RenderStateShard.ShaderStateShard(BeyondShaders::getRefugeGradient);

    public static final Function<ResourceLocation, RenderType> REFUGE_GRADIENT = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(REFUGE_GRADIENT_SHADER_STATE)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("refuge_gradient", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    public static RenderType getRefugeGradient(ResourceLocation location) {
        return REFUGE_GRADIENT.apply(location);
    }

    public static RenderType unlitTranslucent(ResourceLocation textureLocation) {
        RenderType.CompositeState renderState = CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_UNLIT_SHADER).setTextureState(new RenderStateShard.TextureStateShard(textureLocation, false, false)).setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY).setCullState(RenderType.CULL).setLightmapState(RenderType.LIGHTMAP).setOverlayState(RenderType.OVERLAY).createCompositeState(true);
        return RenderType.create("entity_unlit_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false, renderState);
    }
    public static RenderType getEntityDepth(ResourceLocation location) {
        return ENTITY_DEPTH.apply(location);
    }

    public static final Function<ResourceLocation, RenderType> ENTITY_DEPTH = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(DEPTH_SHADER_STATE)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(CRUMBLING_TRANSPARENCY)
                .setCullState(CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("entity_depth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    // entityTranslucent variant with back-face culling enabled.
    // Vanilla entityTranslucent uses NO_CULL, rendering both sides of every quad.
    // Lantern fins are zero-thickness cubes (e.g. 4×0×7) — Minecraft generates two
    // coplanar quads facing opposite directions for them. Without culling, both quads
    // render at the exact same depth, causing z-fighting ("scribbled" artifact).
    // The original ENTITY_DEPTH and unlitTranslucent both use CULL; this variant
    // preserves that behavior for the Iris/Oculus shader fallback path.
    public static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_CULLED = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("entity_translucent_culled", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    public static RenderType entityTranslucentCulled(ResourceLocation location) {
        return ENTITY_TRANSLUCENT_CULLED.apply(location);
    }

    // entityTranslucent variant WITHOUT back-face culling.
    // Used for the Lantern body (solid 3D cube) in the two-pass rendering split:
    // the body needs NO_CULL so both the front and back faces render, giving the
    // translucent entity a volumetric look (you see the back-inner surface through
    // the translucent front). Fins keep CULL via entityTranslucentCulled because
    // they're zero-thickness quads that would z-fight without it.
    // Same shader/transparency/lightmap/overlay state as ENTITY_TRANSLUCENT_CULLED,
    // only the cull state differs — keeps both passes visually consistent.
    public static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_NO_CULLED = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("entity_translucent_no_culled", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    public static RenderType entityTranslucentNoCulled(ResourceLocation location) {
        return ENTITY_TRANSLUCENT_NO_CULLED.apply(location);
    }

    // entityTranslucentEmissive variant with back-face culling.
    // Triggers Iris shader pack bloom/glow effects while preventing z-fighting on
    // zero-thickness fin quads. Uses COLOR_WRITE (no depth write) like vanilla
    // entityTranslucentEmissive — the base pass already established depth.
    public static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_EMISSIVE_CULLED = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(CULL)
                .setWriteMaskState(COLOR_WRITE)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("entity_translucent_emissive_culled", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    public static RenderType entityTranslucentEmissiveCulled(ResourceLocation location) {
        return ENTITY_TRANSLUCENT_EMISSIVE_CULLED.apply(location);
    }
}