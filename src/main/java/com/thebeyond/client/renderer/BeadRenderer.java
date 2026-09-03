package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.renderer.util.LivingBlockMeshBaker;
import com.thebeyond.common.entity.BeadEntity;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.TrinketGrowth;
import com.thebeyond.common.entity.util.livingblock.TrinketGrowth.*;
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
    public static ResourceLocation SPIKE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/spike.png");

    private static final AABB[][] SPIKE_AABBS = new AABB[6][4];
    static {
        for (int d = 0; d < 6; d++) {
            for (int size = 0; size <= 3; size++) {
                Direction dir = Direction.values()[d];
                float height = switch (size) {
                    case 1 -> 2/16f;
                    case 2 -> 4/16f;
                    case 3 -> 6/16f;
                    default -> 0;
                };
                float base = 2/16f;

                SPIKE_AABBS[d][size] = switch (dir) {
                    case UP -> new AABB(0, 0, 0, base, height, base);
                    case DOWN -> new AABB(0, -height, 0, base, 0, base);
                    case NORTH -> new AABB(0, 0, -height, base, base, 0);
                    case SOUTH -> new AABB(0, 0, 0, base, base, height);
                    case WEST -> new AABB(-height, 0, 0, 0, base, base);
                    case EAST -> new AABB(0, 0, 0, height, base, base);
                };
            }
        }
    }

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

        List<Feature> plan = trinket.getFeaturePlan();
        int stage = trinket.getGrowthStage();

        int tWidth = trinket.getWidth();
        int tHeight = trinket.getHeight();
        int tDepth = trinket.getDepth();
        if (plan == null) return;

        for (Feature f : plan) {
            SizeClass size = f.sizeAt(stage);
            Direction d = f.face();
            int width = f.getWidth(size);
            int x = f.x();
            int y = f.y();

            boolean isSpike = f.kind() == Kind.SPIKE;
            final int offset = 3 - (isSpike ? 2 : size.toInt());
            if (size.toInt() == 0) continue;

            switch (d) {
                case UP -> {
                    if (x + width > tWidth || y + width > tDepth) continue;
                    RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(f.getTexture(size))), matrix, normalMatrix, packedLight, r*255, g*255, b*255, 230F, Direction.Axis.Y, true, minX+(x/16f), maxY+(0.0001f*offset), minZ+(y/16f), width, width);
                    if (isSpike) renderSpike(getAabb(d, size.toInt()), minX+((x+1)/16f), maxY+(0.0001f*offset), minZ+((y+1)/16f), poseStack, buffer, packedLight,r*255, g*255, b*255, 230F );
                }
                case DOWN -> {
                    if (x + width > tWidth || y + width > tDepth) continue;
                    RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(f.getTexture(size))), matrix, normalMatrix, packedLight, r * 255, g * 255, b * 255, 230, Direction.Axis.Y, false, minX+(x/16f), minY-(0.0001f*offset), minZ+(y/16f), width, width);
                    if (isSpike) renderSpike(getAabb(d, size.toInt()), minX+((x+1)/16f), minY-(0.0001f*offset), minZ+((y+1)/16f), poseStack, buffer, packedLight,r*255, g*255, b*255, 230F );
                }
                case WEST -> {
                    if (x + width > tHeight || y + width > tDepth) continue;
                    RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(f.getTexture(size))), matrix, normalMatrix, packedLight, r * 255, g * 255, b * 255, 230, Direction.Axis.X, true, minX-(0.0001f*offset), minY+(x/16f), minZ+(y/16f), width, width);
                    if (isSpike) renderSpike(getAabb(d, size.toInt()), minX-(0.0001f*offset), minY+((x+1)/16f), minZ+((y+1)/16f), poseStack, buffer, packedLight,r*255, g*255, b*255, 230F );
                }
                case EAST -> {
                    if (x + width > tHeight || y + width > tDepth) continue;
                    RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(f.getTexture(size))), matrix, normalMatrix, packedLight, r * 255, g * 255, b * 255, 230, Direction.Axis.X, false, maxX+(0.0001f*offset), minY+(x/16f), minZ+(y/16f), width, width);
                    if (isSpike) renderSpike(getAabb(d, size.toInt()), maxX+(0.0001f*offset), minY+((x+1)/16f), minZ+((y+1)/16f), poseStack, buffer, packedLight,r*255, g*255, b*255, 230F );
                }
                case NORTH -> {
                    if (x + width > tWidth || y + width > tHeight) continue;
                    RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(f.getTexture(size))), matrix, normalMatrix, packedLight, r * 255, g * 255, b * 255, 230, Direction.Axis.Z, true, minX+(x/16f), minY+(y/16f), minZ-(0.0001f*offset), width, width);
                    if (isSpike) renderSpike(getAabb(d, size.toInt()), minX+((x+1)/16f), minY+((y+1)/16f), minZ-(0.0001f*offset), poseStack, buffer, packedLight,r*255, g*255, b*255, 230F );
                }
                case SOUTH -> {
                    if (x + width > tWidth || y + width > tHeight) continue;
                    RenderUtils.renderQuadOnAxis(buffer.getBuffer(RenderType.entityCutout(f.getTexture(size))), matrix, normalMatrix, packedLight, r * 255, g * 255, b * 255, 230, Direction.Axis.Z, false, minX+(x/16f), minY+(y/16f), maxZ+(0.0001f*offset), width, width);
                    if (isSpike) renderSpike(getAabb(d, size.toInt()), minX+((x+1)/16f), minY+((y+1)/16f), maxZ+(0.0001f*offset), poseStack, buffer, packedLight,r*255, g*255, b*255, 230F );
                }
            }
        }
    }

    private static AABB getAabb(Direction dir, int size) {
        return SPIKE_AABBS[dir.ordinal()][size];
    }

    public void renderSpike(AABB aabb, float x, float y, float z, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        RenderUtils.renderCuboid(aabb, poseStack, buffer.getBuffer(RenderType.entityCutout(SPIKE)), packedLight, r, g, b, a);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LivingBlock entity) {
        if (entity instanceof BeadEntity beadEntity)
            return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/bauble/" + beadEntity.getVariant() + ".png");
        return null;
    }
}