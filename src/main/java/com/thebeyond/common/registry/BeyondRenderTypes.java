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

    // CULL (vanilla uses NO_CULL): stops coplanar zero-thickness fin quads from z-fighting.
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

    // NO_CULL renders back faces too for a volumetric look through the translucent front.
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

    // Unlit shader skips face-normal shading so coplanar quads match brightness; no-shaderpack only.
    public static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_NO_CULLED_UNLIT = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_UNLIT_SHADER)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("entity_translucent_no_culled_unlit", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    public static RenderType entityTranslucentNoCulledUnlit(ResourceLocation location) {
        return ENTITY_TRANSLUCENT_NO_CULLED_UNLIT.apply(location);
    }

    // COLOR_WRITE, no depth-write: base pass already set depth; CULL stops fin z-fighting.
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

    // NO_CULL projects bloom on both sides; safe ONLY for fully-transparent DOWN UV, else quads z-fight.
    public static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_EMISSIVE_NO_CULLED = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("entity_translucent_emissive_no_culled", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    public static RenderType entityTranslucentEmissiveNoCulled(ResourceLocation location) {
        return ENTITY_TRANSLUCENT_EMISSIVE_NO_CULLED.apply(location);
    }

    static RenderStateShard.ShaderStateShard MIRROR_SHADER_STATE = new RenderStateShard.ShaderStateShard(BeyondShaders::getMirror);

    public static final Function<ResourceLocation, RenderType> MIRROR = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(MIRROR_SHADER_STATE)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                // COLOR_WRITE, no depth-write: occluded by real geometry; coplanar faces never cull each other.
                .setWriteMaskState(COLOR_WRITE)
                // CONSTANT offset, not VIEW_OFFSET_Z_LAYERING whose distance-scaled bias collapses up close.
                .setLayeringState(POLYGON_OFFSET_LAYERING)
                .createCompositeState(false);
        return create("mirror", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false, compositeState);
    });

    public static RenderType mirror(ResourceLocation location) {
        return MIRROR.apply(location);
    }

    // Iris-compatible path: vanilla shader, COLOR_WRITE no depth-write as in MIRROR.
    public static final Function<ResourceLocation, RenderType> MIRROR_PACK = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("mirror_pack", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    public static RenderType mirrorPack(ResourceLocation location) {
        return MIRROR_PACK.apply(location);
    }

    // Depth-only reflection-FBO occluder; NO_CULL because the reflection matrix flips winding.
    public static final RenderType MIRROR_OCCLUDER = create(
            "mirror_occluder",
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_SHADER)
                    .setWriteMaskState(DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // Soft dark blob at the occluder coverage limit; VIEW_OFFSET_Z_LAYERING wins LEQUAL over the coplanar occluder.
    public static final RenderType MIRROR_OUTLINE = create(
            "mirror_shade",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
}