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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.List;

public class BeadRenderer extends LivingBlockRenderer {
    ResourceLocation HOLE_BIG = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/hole_big.png");
    ResourceLocation HOLE_MEDIUM = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/hole_medium.png");
    ResourceLocation HOLE_SMALL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/hole_small.png");
    public BeadRenderer(final EntityRendererProvider.Context context) {
        super(context, "textures/entity/bauble/edges.png", "textures/entity/bauble/outline.png");
    }

    @Override
    protected void renderShape(LivingBlock entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Color color = Color.white;
        Color color2 = new Color(DyeColor.WHITE.getTextureDiffuseColor());
        if (entity instanceof BeadEntity beadEntity) {
            color = beadEntity.getBodyColor();
            Color c = new Color(beadEntity.getDyeColor().getTextureDiffuseColor());

            color2 = new Color(FastColor.ARGB32.lerp(0.6f, color.getRGB(), c.getRGB()));
        }

        List<AABB> shape = entity.getShapeBoxes();
        RenderUtils.renderCuboid(entity.getShapeBounds().deflate(0.001), poseStack, buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity))), packedLight, (color.getRed()*0.9f)/255f, (color.getGreen()*0.9f)/255f, (color.getBlue()*0.9f)/255f, 1);
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
                emit(rim, matrix, normalMatrix, quad, packedLight, color.getRed(), color.getGreen(), color.getBlue(), 200);
            }
        }

        if (entity instanceof BeadEntity trinket)
            renderAdditional(trinket,  matrix, normalMatrix, poseStack, buffer, packedLight, color2.getRed(), color2.getGreen(), color2.getBlue(), 1);
    }

    private void renderAdditional(BeadEntity trinket, Matrix4f matrix, Matrix3f normalMatrix, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float r, float g, float b, float a) {
        AABB shapeBounds = trinket.getShapeBounds().inflate(0.002);
        float maxX = (float) shapeBounds.max(Direction.Axis.X);
        float maxY = (float) shapeBounds.max(Direction.Axis.Y);
        float maxZ = (float) shapeBounds.max(Direction.Axis.Z);
        float minX = (float) shapeBounds.min(Direction.Axis.X);
        float minY = (float) shapeBounds.min(Direction.Axis.Y);
        float minZ = (float) shapeBounds.min(Direction.Axis.Z);

        byte[][] up = trinket.getGrowth(Direction.UP);

        for (int i = 0; i < up.length; i++) {
            for (int j = 0; j < up[i].length; j++) {
                if (up[i][j]==(byte)0) continue;
                if (up[i][j]==(byte)-1) RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(HOLE_SMALL)), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230, Direction.Axis.Y, true, minX+(i/16f), maxY+0.002f, minZ+(j/16f),  3, 3);
                if (up[i][j]==(byte)-2) RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(HOLE_MEDIUM)), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230, Direction.Axis.Y, true, minX+(i/16f), maxY+0.001f, minZ+(j/16f),  4, 4);
                if (up[i][j]==(byte)-3) RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(HOLE_BIG)), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230, Direction.Axis.Y, true, minX+(i/16f), maxY, minZ+(j/16f),  7, 7);
            }
        }

        RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(HOLE_SMALL)), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230, Direction.Axis.Y, false, minX, minY, minZ, 3, 3);
        RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(HOLE_SMALL)), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230, Direction.Axis.X, true, minX, minY, minZ,  3, 3);
        RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(HOLE_SMALL)), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230, Direction.Axis.X, false, maxX, minY, minZ, 3, 3);
        RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(HOLE_SMALL)), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230, Direction.Axis.Z, true, minX, minY, minZ,  3, 3);
        RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(HOLE_SMALL)), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230, Direction.Axis.Z, false, minX, minY, maxZ, 3, 3);
    }

    @Override
    public ResourceLocation getTextureLocation(LivingBlock entity) {
        if (entity instanceof BeadEntity beadEntity)
            return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/" + beadEntity.getVariant() + ".png");
        return null;
    }
}