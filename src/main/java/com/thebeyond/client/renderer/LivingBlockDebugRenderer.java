package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.util.livingblock.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TheBeyond.MODID, value = Dist.CLIENT)
public class LivingBlockDebugRenderer {

    private static final int EDGE_COUNT = 8;
    private static final int[] EDGES = {
            0, 1, 1, 3, 3, 2, 2, 0,
            4, 5, 5, 7, 7, 6, 6, 4,
            0, 4, 1, 5, 2, 6, 3, 7};

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes() || mc.level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        RenderType lines = RenderType.lines();
        VertexConsumer buffer = bufferSource.getBuffer(lines);
        Vec3 cameraPos = event.getCamera().getPosition();

        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Quaternionf interpolatedRot = new Quaternionf();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingBlock livingBlock) {
                livingBlock.getRotation(interpolatedRot, partialTicks);
                Vec3 renderedPosition = livingBlock.getPosition(partialTicks)
                        .add(livingBlock.climbRenderOffset(interpolatedRot, partialTicks));

                for (OrientedBox obb : LivingBlockCollisionHandler.getOBBs(
                        livingBlock, interpolatedRot, renderedPosition)) {
                    drawOBB(poseStack, buffer, obb, 0.0F, 1.0F, 0.0F, 1.0F);
                }
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(lines);
    }

    private static void drawOBB(PoseStack poseStack, VertexConsumer buffer, OrientedBox obb, float r, float g, float b, float a) {
        Vector3f center = obb.getCenter();
        Vector3f extents = obb.getExtents();
        Vector3f[] axes = obb.getAxes();
        Vector3f axisX = axes[0];
        Vector3f axisY = axes[1];
        Vector3f axisZ = axes[2];

        Vector3f[] corners = new Vector3f[EDGE_COUNT];
        for (int i = 0; i < corners.length; i++) {
            float sx = (i & 1) == 0 ? -extents.x : extents.x;
            float sy = (i & 2) == 0 ? -extents.y : extents.y;
            float sz = (i & 4) == 0 ? -extents.z : extents.z;

            corners[i] = new Vector3f(center)
                    .add(axisX.x * sx, axisX.y * sx, axisX.z * sx)
                    .add(axisY.x * sy, axisY.y * sy, axisY.z * sy)
                    .add(axisZ.x * sz, axisZ.y * sz, axisZ.z * sz);
        }

        PoseStack.Pose pose = poseStack.last();

        for (int edge = 0; edge < EDGES.length; edge += 2) {
            drawLine(corners[EDGES[edge]], corners[EDGES[edge + 1]], pose, buffer, r, g, b, a);
        }
    }

    private static void drawLine(Vector3f v1, Vector3f v2, PoseStack.Pose pose, VertexConsumer buffer, float r, float g, float b, float a) {
        float normalX = v2.x() - v1.x();
        float normalY = v2.y() - v1.y();
        float normalZ = v2.z() - v1.z();
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length != 0) {
            normalX /= length;
            normalY /= length;
            normalZ /= length;
        }

        buffer.addVertex(pose, v1.x(), v1.y(), v1.z()).setColor(r, g, b, a).setNormal(pose, normalX, normalY, normalZ);
        buffer.addVertex(pose, v2.x(), v2.y(), v2.z()).setColor(r, g, b, a).setNormal(pose, normalX, normalY, normalZ);
    }
}
