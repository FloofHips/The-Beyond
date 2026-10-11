package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.thebeyond.common.data.ProjectorTexture;
import com.thebeyond.common.registry.BeyondShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Deferred screen-space decal: reconstructs each opaque pixel's world pos and tests it against the projector depth map. */
public final class ProjectorDeferredDecal {
    private static final float BIAS = 0.0015f;  // z-fight floor, the shader's texel-scaled term does the real work
    private static final float TEXEL = 1.0f / ProjectorDepthMap.BASE_RES; // PCF tap spacing

    private static TextureTarget sceneDepthCopy;
    private static boolean fabulousSnapshot;

    private ProjectorDeferredDecal() {
    }

    /** Fabulous keeps each layer's depth in its own target and main loses them at the composite, so merge them first. */
    public static void snapshotFabulousDepth(LevelRenderer lr) {
        RenderTarget translucent = lr.getTranslucentTarget();
        if (translucent == null || ProjectorBlockEntity.LOADED.isEmpty() || !ProjectorRenderer.deferredAvailable()) {
            return;
        }
        // the translucent target starts as a copy of main's depth, so it already holds opaques and entities
        ensureSceneDepth(translucent, translucent.width, translucent.height);
        sceneDepthCopy.copyDepthFrom(translucent);
        fabulousSnapshot = true;
        ShaderInstance merge = BeyondShaders.getProjectorDepthMerge();
        if (merge == null) {
            return;
        }
        ShaderInstance prevShader = RenderSystem.getShader();
        sceneDepthCopy.bindWrite(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);
        RenderSystem.depthMask(true);
        RenderSystem.setShader(() -> merge);
        try {
            merge.safeGetUniform("ScreenSize").set((float) sceneDepthCopy.width, (float) sceneDepthCopy.height);
            for (RenderTarget layer : new RenderTarget[] {lr.getItemEntityTarget(), lr.getParticlesTarget(),
                    lr.getCloudsTarget(), lr.getWeatherTarget()}) {
                if (layer != null && layer.width == sceneDepthCopy.width && layer.height == sceneDepthCopy.height) {
                    RenderSystem.setShaderTexture(0, layer.getDepthTextureId());
                    drawFullscreen();
                }
            }
        } finally {
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableCull();
            RenderSystem.setShader(() -> prevShader);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }
    }

    /** postFinal under an Iris pack: the main target holds the final image and full depth, so the hand cutoff applies. */
    public static void draw(Matrix4f projIn, Matrix4f viewIn, boolean postFinal) {
        boolean snapshot = fabulousSnapshot;
        fabulousSnapshot = false;
        if (ShaderCompatLib.isShadowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ShaderInstance shader = BeyondShaders.getProjectorDecal();
        if (shader == null) {
            return;
        }
        List<ProjectorDepthMap.Active> active = ProjectorDepthMap.activeThisFrame();
        if (active.isEmpty()) {
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        if (w <= 0 || h <= 0) {
            return;
        }

        // the decal reads this copy while the cone draws into main, so it never reads what it writes
        if (!snapshot || sceneDepthCopy.width != w || sceneDepthCopy.height != h) {
            ensureSceneDepth(main, w, h);
            sceneDepthCopy.copyDepthFrom(main);
        }
        main.bindWrite(true);

        Matrix4f invVP = new Matrix4f(projIn).mul(viewIn).invert();

        ShaderInstance prevShader = RenderSystem.getShader();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();   // depth test happens per-pixel in-shader (cone compare vs projector map)
        RenderSystem.setShader(() -> shader);

        try {
            // F3 false-color diagnostics: open=1, +sneak=2 (aliasing), +sprint=3 (fade), else off.
            shader.safeGetUniform("DebugMode").set(!mc.getDebugOverlay().showDebugScreen() ? 0.0f
                    : (mc.player != null && mc.player.isShiftKeyDown() ? 2.0f
                    : (mc.options.keySprint.isDown() ? 3.0f : 1.0f)));
            for (ProjectorDepthMap.Active a : active) {
                ProjectorBlockEntity be = a.be();
                if (be.isRemoved() || be.getLevel() != mc.level) {
                    continue;
                }
                shader.safeGetUniform("InverseViewProj").set(invVP);
                shader.safeGetUniform("ProjectorViewProj").set(a.vp());
                Vector3f e = a.eyeRel();
                shader.safeGetUniform("ProjectorEye").set(e.x, e.y, e.z);
                shader.safeGetUniform("ScreenSize").set((float) w, (float) h);
                shader.safeGetUniform("ConeParams").set(a.coneK(), (float) ProjectorRenderer.MAX_THROW, BIAS, TEXEL);
                RenderSystem.setShaderTexture(1, sceneDepthCopy.getDepthTextureId());
                RenderSystem.setShaderTexture(2, a.depthColorTexId());
                RenderSystem.setShaderTexture(3, a.depthFarTexId());

                drawProjectorSlots(shader, be, postFinal);
            }
        } finally {
            // units 1 and 2 are the lightmap and overlay in vanilla entity shaders, so clear them
            RenderSystem.setShaderTexture(1, 0);
            RenderSystem.setShaderTexture(2, 0);
            RenderSystem.setShaderTexture(3, 0);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> prevShader);
        }
    }

    private static void drawProjectorSlots(ShaderInstance shader, ProjectorBlockEntity be, boolean postFinal) {
        NonNullList<ItemStack> items = be.getItems();
        int mode = be.getMode();
        ResourceLocation gradeId = ProjectorRenderer.frontGlassGradeId(be);
        int[] filled = be.filledSlots();
        int f = filled.length;
        if (f == 0) {
            return;
        }
        int carousel = Math.floorMod(be.getCarouselIndex(), f);
        for (int j = 0; j < f; j++) {
            int slot = filled[j];
            ProjectorRenderer.Resolved base = ProjectorRenderer.resolveTexture(items.get(slot), gradeId);
            if (base == null) {
                continue;
            }
            if (mode == ProjectorBlockEntity.MODE_CAROUSEL && j != carousel) {
                continue;
            }
            ProjectorTexture.Region region = ProjectorRenderer.regionFor(mode, j, f, base.region());
            shader.safeGetUniform("ImageRegion").set(region.u0(), region.v0(), region.u1(), region.v1());
            shader.safeGetUniform("Flags").set(base.flipV() ? 1.0f : 0.0f, ProjectorTunables.SHADOW_STRENGTH, base.opacity(),
                    postFinal ? ProjectorTunables.NEAR_CUTOFF : 0.0f);
            RenderSystem.setShaderTexture(0, base.texture());
            drawFullscreen();
        }
    }

    private static void drawFullscreen() {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.addVertex(-1.0f, -1.0f, 0.0f);
        bb.addVertex(1.0f, -1.0f, 0.0f);
        bb.addVertex(1.0f, 1.0f, 0.0f);
        bb.addVertex(-1.0f, 1.0f, 0.0f);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void ensureSceneDepth(RenderTarget src, int w, int h) {
        // a depth blit needs matching formats, and stencil can't be turned off on a target
        if (sceneDepthCopy != null && sceneDepthCopy.isStencilEnabled() && !src.isStencilEnabled()) {
            sceneDepthCopy.destroyBuffers();
            sceneDepthCopy = null;
        }
        if (sceneDepthCopy == null) {
            sceneDepthCopy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            sceneDepthCopy.setFilterMode(GL11.GL_NEAREST); // NEAREST: no depth interpolation across silhouettes
        } else if (sceneDepthCopy.width != w || sceneDepthCopy.height != h) {
            sceneDepthCopy.resize(w, h, Minecraft.ON_OSX);
            sceneDepthCopy.setFilterMode(GL11.GL_NEAREST);
        }
        if (src.isStencilEnabled() && !sceneDepthCopy.isStencilEnabled()) {
            sceneDepthCopy.enableStencil();
        }
    }
}
