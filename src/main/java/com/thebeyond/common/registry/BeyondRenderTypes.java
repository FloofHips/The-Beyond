package com.thebeyond.common.registry;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
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

    // CULL (vanilla uses NO_CULL): stops coplanar zero-thickness fin quads z-fighting.
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

    // No-shaderpack only: unlit shader has no Iris equivalent.
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

    // NO_CULL safe ONLY when the DOWN UV is fully transparent, else quads z-fight.
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
    static RenderStateShard.ShaderStateShard PROJECTOR_DIST_SHADER_STATE = new RenderStateShard.ShaderStateShard(BeyondShaders::getProjectorDist);
    static RenderStateShard.ShaderStateShard PROJECTOR_DIST_PEEL_SHADER_STATE = new RenderStateShard.ShaderStateShard(BeyondShaders::getProjectorDistPeel);
    static RenderStateShard.ShaderStateShard PROJECTOR_DIST_ENTITY_SHADER_STATE = new RenderStateShard.ShaderStateShard(BeyondShaders::getProjectorDistEntity);

    public static final Function<ResourceLocation, RenderType> MIRROR = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(MIRROR_SHADER_STATE)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                // Constant offset: VIEW_OFFSET_Z_LAYERING's distance-scaled bias collapses up close.
                .setLayeringState(POLYGON_OFFSET_LAYERING)
                .createCompositeState(false);
        return create("mirror", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false, compositeState);
    });

    public static RenderType mirror(ResourceLocation location) {
        return MIRROR.apply(location);
    }

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

    // Stacked layers composite by submission order; an aggressive shaderpack sort may break it.
    public static final Function<ResourceLocation, RenderType> PROJECTOR_PACK = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("projector_pack", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, compositeState);
    });

    public static RenderType projectorPack(ResourceLocation location) {
        return PROJECTOR_PACK.apply(location);
    }

    // NO_CULL because the reflection matrix flips winding.
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

    // Block atlas bound for the cutout alpha test, else torch-family transparent full-block quads occlude.
    public static final RenderType PROJECTOR_DEPTH_BLOCK = create(
            "projector_depth_block",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(PROJECTOR_DIST_SHADER_STATE)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // Depth peel: shader discards anything at/within the first layer, which must be bound as Sampler1.
    public static final RenderType PROJECTOR_DEPTH_BLOCK_PEEL = create(
            "projector_depth_block_peel",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(PROJECTOR_DIST_PEEL_SHADER_STATE)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // Masks off B so the blocks-only distance survives under entity fragments (clips entity shadows to it).
    private static final WriteMaskStateShard COLOR_NO_B_DEPTH_WRITE = new WriteMaskStateShard(true, true) {
        @Override
        public void setupRenderState() {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
            com.mojang.blaze3d.systems.RenderSystem.colorMask(true, true, false, true);
        }

        @Override
        public void clearRenderState() {
            com.mojang.blaze3d.systems.RenderSystem.colorMask(true, true, true, true);
        }
    };

    // Must draw into the same FBO after PROJECTOR_DEPTH_BLOCK.
    public static final Function<ResourceLocation, RenderType> PROJECTOR_DEPTH_ENTITY = Util.memoize((location) -> {
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(PROJECTOR_DIST_ENTITY_SHADER_STATE)
                .setTextureState(new TextureStateShard(location, false, false))
                .setWriteMaskState(COLOR_NO_B_DEPTH_WRITE)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setCullState(NO_CULL)
                .createCompositeState(false);
        return create("projector_depth_entity", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, compositeState);
    });

    public static RenderType projectorDepthEntity(ResourceLocation location) {
        return PROJECTOR_DEPTH_ENTITY.apply(location);
    }

    // VIEW_OFFSET_Z_LAYERING lets this win LEQUAL over the coplanar occluder.
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