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

    // Unlit NO_CULL variant — same geometry as ENTITY_TRANSLUCENT_NO_CULLED
    // (back+front faces rendered → volumetric translucent look for the solid
    // 3D body cube) but uses entity_translucent_unlit instead of the standard
    // entity_translucent shader. The unlit shader skips Mojang's face-normal
    // shading (which would make UP ≈ 1.0 / DOWN ≈ 0.4), so coplanar zero-thickness
    // fin/tail quads render with uniform brightness top and bottom — fixes the
    // hue mismatch the vanilla translucent shader produces.
    //
    // Only safe to use when no Iris/Oculus shaderpack is actively running:
    // a live pack replaces the entire shader pipeline via G-Buffer, ignoring
    // custom shader state. Gate via ShaderCompatLib.isShaderPackActive() and
    // fall back to ENTITY_TRANSLUCENT_NO_CULLED when a pack is in use.
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

    // entityTranslucentEmissive variant WITHOUT back-face culling.
    // SAFE ONLY for atlases whose zero-thickness fin/tail DOWN UV regions are
    // fully transparent (α=0) — otherwise the two coplanar quads (UP and DOWN)
    // both rasterize opaque content at the same depth, producing the
    // "scribble" z-fighting artifact that COLOR_WRITE doesn't prevent (it
    // suppresses depth-buffer flicker, not rasterizer coverage overlap).
    //
    // In this mod: use ONLY for the Leviathan lantern — its leviathan_lantern.png
    // was confirmed via .backups/DumpAtlasUVs to have α=0% DOWN UV on every
    // fin/tail face. Small / Medium / Large lanterns all have DOWN UV identical
    // to UP with opaque content (confirmed via .backups/DumpSmallerLanternUVs),
    // so they MUST keep entityTranslucentEmissiveCulled.
    //
    // Purpose: under an active Iris shaderpack, CULL from below strips the UP
    // face (back-facing from camera), and the DOWN face being transparent on
    // the Leviathan means zero bloom contribution on the underside of fins/tail.
    // NO_CULL renders the UP face from its back side too, so its opaque pixels
    // project bloom uniformly top and bottom.
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
}