package com.thebeyond.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.common.camera.SnapshotGrade;
import com.thebeyond.common.registry.BeyondShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Must run from {@link #runQueued()} outside {@code renderLevel}, else binding our FBOs corrupts the player's frame. */
public final class ItemIconTextures {
    private static final int SIZE = 64;
    private static final int MAX_SLOTS = 24;
    private static final int RENDER_GRACE = 2;
    private static final boolean OSX = Minecraft.ON_OSX;

    private static final class Key {
        final ItemStack stack;
        final SnapshotGrade grade;
        private final int hash;

        Key(ItemStack stack, SnapshotGrade grade) {
            this.stack = stack;
            this.grade = grade;
            this.hash = 31 * ItemStack.hashItemAndComponents(stack) + grade.ordinal();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && grade == k.grade && ItemStack.isSameItemSameComponents(stack, k.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class Slot {
        final ResourceLocation loc;
        final FboTexture fbo = new FboTexture();
        final ItemStack stack;
        final SnapshotGrade grade;
        TextureTarget target;
        TextureTarget gradeTarget;  // populated only when grade != NONE and the shader loaded
        long lastRequestedFrame;
        boolean valid;

        Slot(ResourceLocation loc, ItemStack stack, SnapshotGrade grade) {
            this.loc = loc;
            this.stack = stack;
            this.grade = grade;
        }

        void free() {
            Minecraft.getInstance().getTextureManager().release(loc);
            if (target != null) {
                target.destroyBuffers();
                target = null;
            }
            if (gradeTarget != null) {
                gradeTarget.destroyBuffers();
                gradeTarget = null;
            }
            valid = false;
        }
    }

    private static final LinkedHashMap<Key, Slot> POOL =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, Slot> eldest) {
                    if (size() > MAX_SLOTS) {
                        eldest.getValue().free();
                        return true;
                    }
                    return false;
                }
            };

    private static boolean rendering;
    private static long frame;
    private static int idCounter = 0;

    private ItemIconTextures() {
    }

    /** Null until its FBO has rendered at least once. */
    public static ResourceLocation get(ItemStack stack, SnapshotGrade grade) {
        if (stack.isEmpty()) {
            return null;
        }
        Slot slot = POOL.get(new Key(stack, grade));
        if (slot == null) {
            ItemStack copy = stack.copy();
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "dynamic/item_icon/" + (idCounter++));
            slot = new Slot(loc, copy, grade);
            POOL.put(new Key(copy, grade), slot);
        }
        slot.lastRequestedFrame = frame;
        return slot.valid ? slot.loc : null;
    }

    /** Call from {@code RenderFrameEvent.Pre}, client thread. */
    public static void runQueued() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ShaderCompatLib.isShadowPass()) {
            return;
        }
        frame++;
        if (rendering || POOL.isEmpty()) {
            return;
        }
        rendering = true;
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        boolean pushed = false;
        try {
            RenderSystem.backupProjectionMatrix();

            // Pass 1: raw icons, GUI-style ortho.
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, 16f, 16f, 0f, 1000f, 21000f),
                    VertexSorting.ORTHOGRAPHIC_Z);
            mv.pushMatrix();
            pushed = true;
            mv.identity().translate(0f, 0f, -11000f);
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();

            GuiGraphics gg = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            boolean anyGraded = false;
            for (Slot slot : POOL.values()) {
                if (frame - slot.lastRequestedFrame <= RENDER_GRACE) {
                    renderRaw(slot, gg);
                    anyGraded |= slot.grade != SnapshotGrade.NONE;
                }
            }

            // Pass 2: graded slots, identity matrices for a full-screen shader blit.
            if (anyGraded) {
                RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
                mv.identity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.disableBlend();
                for (Slot slot : POOL.values()) {
                    if (slot.grade != SnapshotGrade.NONE && frame - slot.lastRequestedFrame <= RENDER_GRACE) {
                        gradeBlit(slot);
                    }
                }
                RenderSystem.enableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
            }
        } catch (Throwable t) {
            TheBeyond.LOGGER.error("[projector] item-icon render failed", t);
        } finally {
            if (pushed) {
                mv.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
            mc.getMainRenderTarget().bindWrite(true);
            rendering = false;
        }
    }

    private static void renderRaw(Slot slot, GuiGraphics gg) {
        if (slot.target == null) {
            slot.target = new TextureTarget(SIZE, SIZE, true /* depth: 3D block items need it */, OSX);
            slot.target.setFilterMode(GL11.GL_NEAREST);
            Minecraft.getInstance().getTextureManager().register(slot.loc, slot.fbo);
        }
        slot.target.setClearColor(0f, 0f, 0f, 0f);
        slot.target.clear(OSX);
        slot.target.bindWrite(true);

        gg.renderItem(slot.stack, 0, 0);
        gg.flush();

        slot.fbo.setId(slot.target.getColorTextureId()); // ungraded fallback if gradeBlit bails
        slot.valid = true;
    }

    /** On shader miss the slot keeps the raw icon. */
    private static void gradeBlit(Slot slot) {
        ShaderInstance shader = slot.grade == SnapshotGrade.SEPIA ? BeyondShaders.getProjectorGradeSepia()
                : slot.grade == SnapshotGrade.BLUE ? BeyondShaders.getProjectorGradeBlue() : null;
        if (shader == null) {
            return;
        }
        if (slot.gradeTarget == null) {
            slot.gradeTarget = new TextureTarget(SIZE, SIZE, false, OSX);
            slot.gradeTarget.setFilterMode(GL11.GL_NEAREST);
        }
        slot.gradeTarget.setClearColor(0f, 0f, 0f, 0f);
        slot.gradeTarget.clear(OSX);
        slot.gradeTarget.bindWrite(true);

        RenderSystem.setShaderTexture(0, slot.target.getColorTextureId());
        RenderSystem.setShader(() -> shader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        bb.addVertex(1f, -1f, 0f).setUv(1f, 0f);
        bb.addVertex(1f, 1f, 0f).setUv(1f, 1f);
        bb.addVertex(-1f, 1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(bb.buildOrThrow());

        slot.fbo.setId(slot.gradeTarget.getColorTextureId());
    }

    /** GL thread only. */
    public static void release() {
        for (Iterator<Slot> it = POOL.values().iterator(); it.hasNext(); ) {
            it.next().free();
            it.remove();
        }
    }
}
