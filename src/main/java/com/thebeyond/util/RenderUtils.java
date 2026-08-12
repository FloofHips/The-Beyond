package com.thebeyond.util;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.List;

public class RenderUtils {
    public static void renderModel(ResourceLocation loc, PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord) {
        Vector3f colors = new Vector3f(1,1,1);
        renderModel(loc, poseStack, consumer, packedLight, overlayCoord, colors.x, colors.y, colors.z,1);
    }

    public static ModelPart bakeLayer(ModelLayerLocation location) {
        return Minecraft.getInstance().getEntityModels().bakeLayer(location);
    }

    public static void renderModel(ResourceLocation loc, PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord, float r, float g, float b, float a) {
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        ModelManager manager = Minecraft.getInstance().getModelManager();
        for (BakedModel pass : manager.getModel(ModelResourceLocation.standalone(loc)).getRenderPasses(ItemStack.EMPTY, true)) {
            consumer.setColor(r, g, b, a);
            renderer.renderModelLists(pass, ItemStack.EMPTY, packedLight, overlayCoord, poseStack, consumer);
            consumer.setColor(1, 1, 1, 1);
        }
    }

    /** A model resolved once per frame and reused across draws, skipping the per-call ModelManager
     *  lookup + getRenderPasses() allocation. Re-resolved each frame (no cross-frame cache). */
    public static final class ResolvedModel {
        private final List<BakedModel> passes;
        private ResolvedModel(List<BakedModel> passes) { this.passes = passes; }

        public static ResolvedModel resolve(ResourceLocation loc) {
            ModelManager manager = Minecraft.getInstance().getModelManager();
            return new ResolvedModel(manager.getModel(ModelResourceLocation.standalone(loc)).getRenderPasses(ItemStack.EMPTY, true));
        }

        public void emit(PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord, float r, float g, float b, float a) {
            ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
            for (BakedModel pass : passes) {
                consumer.setColor(r, g, b, a);
                renderer.renderModelLists(pass, ItemStack.EMPTY, packedLight, overlayCoord, poseStack, consumer);
                consumer.setColor(1, 1, 1, 1);
            }
        }
    }

    public static void renderCuboid(AABB aabb, PoseStack poseStack, VertexConsumer buffer, int light, float r, float g, float b, float a) {
        var mat4 = poseStack.last().pose();
        var mat3 = poseStack.last().normal();

        float minX = (float) aabb.minX;
        float minY = (float) aabb.minY;
        float minZ = (float) aabb.minZ;

        float maxX = (float) aabb.maxX;
        float maxY = (float) aabb.maxY;
        float maxZ = (float) aabb.maxZ;

        float width = maxX - minX;
        float height = maxY - minY;
        float depth = maxZ - minZ;


        renderQuad(buffer, mat4, mat3, light, r, g, b, a, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0, -1, 0, width, depth);
        renderQuad(buffer, mat4, mat3, light, r, g, b, a, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 0, 1, 0 , depth, width);
        renderQuad(buffer, mat4, mat3, light, r, g, b, a, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, 0, 0, -1, height, width);
        renderQuad(buffer, mat4, mat3, light, r, g, b, a, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0, 0, 1 , width, height);
        renderQuad(buffer, mat4, mat3, light, r, g, b, a, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, -1, 0, 0, depth, height);
        renderQuad(buffer, mat4, mat3, light, r, g, b, a, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, 1, 0, 0 , height, depth);
    }

    public static void renderQuad(VertexConsumer buffer, Matrix4f mat4, Matrix3f mat3, int light, float r, float g, float b, float a, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float nx, float ny, float nz, float uSize, float vSize) {
        float u0 = 0;
        float v0 = 0;

        Vector3f normal = new Vector3f(nx, ny, nz);
        normal.mul(mat3);

        buffer.addVertex(mat4, x3, y3, z3).setColor(r, g, b, a).setUv(uSize, vSize).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x, normal.y, normal.z);
        buffer.addVertex(mat4, x4, y4, z4).setColor(r, g, b, a).setUv(u0, vSize).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x, normal.y, normal.z);
        buffer.addVertex(mat4, x1, y1, z1).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x, normal.y, normal.z);
        buffer.addVertex(mat4, x2, y2, z2).setColor(r, g, b, a).setUv(uSize, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x, normal.y, normal.z);
    }

    public static void renderQuadOnAxis(VertexConsumer buffer, Matrix4f mat4, Matrix3f mat3, int light, float r, float g, float b, float a, Axis axis, boolean pos, float minX, float minY, float minZ, float uSize, float vSize) {
        uSize = uSize / 16f;
        vSize = vSize / 16f;

        if (axis == Axis.Y) {
            float maxX = minX + uSize;
            float maxZ = minZ + vSize;
            if (!pos) renderQuad(buffer, mat4, mat3, light, r, g, b, a, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0, -1, 0, 1, 1);
            if (pos) renderQuad(buffer, mat4, mat3, light, r, g, b, a,  minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0, 1, 0, 1, 1);
            return;
        }
        if (axis == Axis.X) {
            float maxY = minY + uSize;
            float maxZ = minZ + vSize;
            if (!pos) renderQuad(buffer, mat4, mat3, light, r, g, b, a, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, 1, 0, 0, 1, 1);
            if (pos) renderQuad(buffer, mat4, mat3, light, r, g, b, a,  minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, -1, 0, 0 , 1, 1);
            return;
        }
        if (axis == Axis.Z) {
            float maxX = minX + uSize;
            float maxY = minY + vSize;
            if (!pos) renderQuad(buffer, mat4, mat3, light, r, g, b, a, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0, 0, 1, 1, 1);
            if (pos) renderQuad(buffer, mat4, mat3, light, r, g, b, a,  minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, 0, 0, -1 , 1, 1);
            return;
        }
    }
}
