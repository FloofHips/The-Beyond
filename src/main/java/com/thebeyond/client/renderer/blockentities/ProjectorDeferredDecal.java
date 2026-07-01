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
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Deferred screen-space decal: reconstructs each opaque pixel's world pos and tests it against the projector depth map. */
public final class ProjectorDeferredDecal {
    private static final float BIAS = 0.0015f;                        // z-fight floor; fsh's texel-scaled term does the real work
    private static final float TEXEL = 1.0f / ProjectorDepthMap.BASE_RES; // PCF tap spacing

    private static TextureTarget sceneDepthCopy;

    private ProjectorDeferredDecal() {
    }

    /** postFinal=true under an Iris pack: main target holds composited image + full depth; the hand cutoff engages. */
    public static void draw(Matrix4f projIn, Matrix4f viewIn, boolean postFinal) {
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

        // Decal reads this copy while the cone draws into main; avoids read-while-write feedback.
        ensureSceneDepth(w, h);
        sceneDepthCopy.copyDepthFrom(main);
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
            // Units 1/2 double as lightmap/overlay in vanilla entity shaders; clear them.
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
        ResourceLocation gradeId = be.getGradeId();
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

    private static void ensureSceneDepth(int w, int h) {
        if (sceneDepthCopy == null) {
            sceneDepthCopy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            sceneDepthCopy.setFilterMode(GL11.GL_NEAREST); // NEAREST: no depth interpolation across silhouettes
        } else if (sceneDepthCopy.width != w || sceneDepthCopy.height != h) {
            sceneDepthCopy.resize(w, h, Minecraft.ON_OSX);
            sceneDepthCopy.setFilterMode(GL11.GL_NEAREST);
        }
    }
}
