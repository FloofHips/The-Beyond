package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.renderer.util.LivingBlockMeshBaker;
import com.thebeyond.common.entity.BeadEntity;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.registry.BeyondRenderTypes;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.List;

public class BeadRenderer extends LivingBlockRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/swirl.png");


    public BeadRenderer(final EntityRendererProvider.Context context) {
        super(context, "textures/entity/bauble/edges.png", "textures/entity/bauble/outline.png");
    }

    @Override
    protected void renderShape(LivingBlock entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Color color = Color.white;
        if (entity instanceof BeadEntity beadEntity)
            color = beadEntity.getBodyColor();

        List<AABB> shape = entity.getShapeBoxes();
        RenderUtils.renderCuboid(entity.getShapeBounds().deflate(0.001), poseStack, buffer.getBuffer(RenderType.entityCutout(TEXTURE)), packedLight, (color.getRed()*0.9f)/255f, (color.getGreen()*0.9f)/255f, (color.getBlue()*0.9f)/255f, 1);
        List<LivingBlockMeshBaker.MeshQuad> mesh = this.meshCache.computeIfAbsent(shape, boxes -> {
            List<LivingBlockMeshBaker.MeshQuad> baked = LivingBlockMeshBaker.bake(boxes);
            int rimCount = 0;
            for (LivingBlockMeshBaker.MeshQuad q : baked) {
                if (q.rim()) {
                    rimCount++;
                }
            }
            return baked;
        });


        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        VertexConsumer rim = buffer.getBuffer(BeyondRenderTypes.entityTranslucentNoCulled(this.skin.rim()));
        for (LivingBlockMeshBaker.MeshQuad quad : mesh) {
            if (quad.rim()) {
                emit(rim, matrix, normalMatrix, quad, packedLight, color.getRed(), color.getGreen(), color.getBlue(), 230);
            }
        }
    }
}