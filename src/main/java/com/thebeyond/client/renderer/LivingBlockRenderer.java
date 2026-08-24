package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.renderer.util.LivingBlockMeshBaker;
import com.thebeyond.client.renderer.util.LivingBlockSkin;
import com.thebeyond.common.entity.util.livingblock.AABBBuilder;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionHandler;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

import java.lang.Math;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class LivingBlockRenderer extends EntityRenderer<LivingBlock> {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final String DEFAULT_TEXTURE = "memor";
    private static final float SHADOW_RADIUS = 0.4F;

    private final Quaternionf rotation = new Quaternionf();
    protected final Map<List<AABB>, List<LivingBlockMeshBaker.MeshQuad>> meshCache = new WeakHashMap<>();
    protected final LivingBlockSkin skin;

    public LivingBlockRenderer(final EntityRendererProvider.Context context) {
        this(context, LivingBlockSkin.of(TheBeyond.MODID, DEFAULT_TEXTURE));
    }
    public LivingBlockRenderer(final EntityRendererProvider.Context context, String texture) {
        this(context, LivingBlockSkin.of(TheBeyond.MODID, texture));
    }
    public LivingBlockRenderer(final EntityRendererProvider.Context context, String texture, String outline) {
        this(context, LivingBlockSkin.of(TheBeyond.MODID, texture, outline));
    }

    protected LivingBlockRenderer(final EntityRendererProvider.Context context, final LivingBlockSkin skin) {
        super(context);
        this.shadowRadius = SHADOW_RADIUS;
        this.skin = skin.resolved();
    }

    @Override
    public void render(final LivingBlock entity,
                       final float entityYaw,
                       final float partialTicks,
                       final PoseStack poseStack,
                       final MultiBufferSource buffer,
                       final int packedLight) {

        AABB box = entity.getBoundingBox();
        double sizeX = box.getXsize();
        double sizeY = box.getYsize();
        double sizeZ = box.getZsize();

        entity.getRotation(this.rotation, partialTicks);
        Vec3 pivotOffset = entity.climbRenderOffset(this.rotation, partialTicks);

        AABBBuilder builder = new AABBBuilder();
        LivingBlockCollisionHandler.includeRotatedOBBCorners(entity, this.rotation, builder);

        Direction climbDir = entity.getClimbingDirection();
        Direction.Axis axis = climbDir.getAxis();
        int step = climbDir.getAxisDirection().getStep();
        double edgeOffset = builder.edge(climbDir);

        double translateX = 0.0;
        double translateY = 0.0;
        double translateZ = 0.0;

        if (entity.usesClimbPivot()) {
            translateY = -builder.edge(Direction.DOWN);
        } else if (axis == Direction.Axis.Y) {
            translateY = climbDir == Direction.DOWN ? -edgeOffset : sizeY - edgeOffset;
        } else {
            translateY = sizeY * 0.5;
            if (axis == Direction.Axis.X) {
                translateX = sizeX * 0.5 * step - edgeOffset;
            } else {
                translateZ = sizeZ * 0.5 * step - edgeOffset;
            }
        }

        poseStack.pushPose();
        poseStack.translate(translateX + pivotOffset.x, translateY + pivotOffset.y,
                translateZ + pivotOffset.z);

        Vector3fc pogo = entity.getPogoScale(partialTicks);
        poseStack.scale(pogo.x(), pogo.y(), pogo.z());
        poseStack.mulPose(this.rotation);

        AABB bounds = entity.getBaseShapeBounds();
        poseStack.translate(
                -(bounds.minX + bounds.maxX) * 0.5,
                -(bounds.minY + bounds.maxY) * 0.5,
                -(bounds.minZ + bounds.maxZ) * 0.5
        );

        renderShape(entity, poseStack, buffer, packedLight);

        poseStack.popPose();

        Entity leashHolder = entity.getLeashHolder();
        if (leashHolder != null) {
            this.renderLeash(entity, partialTicks, poseStack, buffer, leashHolder);
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    protected void renderShape(LivingBlock entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        List<AABB> shape = entity.getShapeBoxes();
        List<LivingBlockMeshBaker.MeshQuad> mesh = this.meshCache.computeIfAbsent(shape, boxes -> {
            List<LivingBlockMeshBaker.MeshQuad> baked = LivingBlockMeshBaker.bake(boxes);
            int rimCount = 0;
            for (LivingBlockMeshBaker.MeshQuad q : baked) {
                if (q.rim()) {
                    rimCount++;
                }
            }
            LOGGER.info("[livingblock] baked shape: boxes={} quads={} rim={} fill={}",
                    boxes.size(), baked.size(), rimCount, baked.size() - rimCount);
            return baked;
        });

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        VertexConsumer fill = buffer.getBuffer(RenderType.entityCutoutNoCull(this.skin.fill()));
        for (LivingBlockMeshBaker.MeshQuad quad : mesh) {
            if (!quad.rim()) {
                emit(fill, matrix, normalMatrix, quad, packedLight);
            }
        }

        VertexConsumer rim = buffer.getBuffer(RenderType.entityCutoutNoCull(this.skin.rim()));
        for (LivingBlockMeshBaker.MeshQuad quad : mesh) {
            if (quad.rim()) {
                emit(rim, matrix, normalMatrix, quad, packedLight);
            }
        }
    }

    public static void emit(final VertexConsumer consumer, final Matrix4f matrix, Matrix3f normalMatrix, final LivingBlockMeshBaker.MeshQuad quad, final int packedLight) {
        emit(consumer, matrix, normalMatrix, quad, packedLight, 255, 255, 255, 255);
    }

    public static void emit(final VertexConsumer consumer, final Matrix4f matrix, Matrix3f normalMatrix, final LivingBlockMeshBaker.MeshQuad quad, final int packedLight, int r, int g, int b, int a) {
        float[] xyz = quad.xyz();
        float[] uv = quad.uv();

        Vector3f normal = new Vector3f(quad.nx(), quad.ny(), quad.nz());
        normal.mul(normalMatrix);

        for (int i = 0; i < 4; i++) {
            consumer.addVertex(matrix, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .setColor(r, g, b, a)
                    .setUv(uv[i * 2], uv[i * 2 + 1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(normal.x, normal.y, normal.z);
        }
    }

        @Override
    public ResourceLocation getTextureLocation(final LivingBlock entity) {
        return this.skin.rim();
    }

    private void renderLeash(final LivingBlock entity, final float partialTicks, final PoseStack poseStack,
                             final MultiBufferSource buffer, final Entity holder) {
        poseStack.pushPose();

        Vec3 ropeHold = holder.getRopeHoldPosition(partialTicks);
        double yaw = Mth.lerp(partialTicks, entity.yBodyRotO, entity.yBodyRot) * (Math.PI / 180.0) + (Math.PI / 2.0);
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);

        Vec3 leashOffset = entity.getLeashOffset(partialTicks);
        double offX = cosYaw * leashOffset.z + sinYaw * leashOffset.x;
        double offZ = sinYaw * leashOffset.z - cosYaw * leashOffset.x;

        double startX = Mth.lerp((double) partialTicks, entity.xo, entity.getX()) + offX;
        double startY = Mth.lerp((double) partialTicks, entity.yo, entity.getY()) + leashOffset.y;
        double startZ = Mth.lerp((double) partialTicks, entity.zo, entity.getZ()) + offZ;

        poseStack.translate(offX, leashOffset.y, offZ);

        float dx = (float) (ropeHold.x - startX);
        float dy = (float) (ropeHold.y - startY);
        float dz = (float) (ropeHold.z - startZ);

        VertexConsumer rope = buffer.getBuffer(RenderType.leash());
        Matrix4f matrix = poseStack.last().pose();
        float inv = (float) (Mth.invSqrt(dx * dx + dz * dz) * 0.0125F);
        float sideX = dz * inv;
        float sideZ = dx * inv;

        BlockPos entityLightPos = BlockPos.containing(entity.getEyePosition(partialTicks));
        BlockPos holderLightPos = BlockPos.containing(holder.getEyePosition(partialTicks));
        int entityBlockLight = this.getBlockLightLevel(entity, entityLightPos);
        int holderBlockLight = entity.level().getBrightness(LightLayer.BLOCK, holderLightPos);
        int entitySkyLight = entity.level().getBrightness(LightLayer.SKY, entityLightPos);
        int holderSkyLight = entity.level().getBrightness(LightLayer.SKY, holderLightPos);

        for (int i = 0; i <= 24; i++) {
            addLeashVertexPair(rope, matrix, dx, dy, dz, entityBlockLight, holderBlockLight,
                    entitySkyLight, holderSkyLight, 0.025F, 0.025F, sideX, sideZ, i, false);
        }
        for (int i = 24; i >= 0; i--) {
            addLeashVertexPair(rope, matrix, dx, dy, dz, entityBlockLight, holderBlockLight,
                    entitySkyLight, holderSkyLight, 0.025F, 0.0F, sideX, sideZ, i, true);
        }

        poseStack.popPose();
    }

    private static void addLeashVertexPair(final VertexConsumer rope, final Matrix4f matrix,
                                           final float dx, final float dy, final float dz,
                                           final int entityBlockLight, final int holderBlockLight,
                                           final int entitySkyLight, final int holderSkyLight,
                                           final float width, final float yOffset,
                                           final float sideX, final float sideZ,
                                           final int index, final boolean end) {
        float t = index * 0.041666668F;
        int blockLight = (int) Mth.lerp(t, (float) entityBlockLight, (float) holderBlockLight);
        int skyLight = (int) Mth.lerp(t, (float) entitySkyLight, (float) holderSkyLight);
        int packedLight = LightTexture.pack(blockLight, skyLight);

        float shade = index % 2 == (end ? 1 : 0) ? 0.7F : 1.0F;
        float r = 0.5F * shade;
        float g = 0.4F * shade;
        float b = 0.3F * shade;

        float x = dx * t;
        float y = dy > 0.0F ? dy * t * t : dy - dy * (1.0F - t) * (1.0F - t);
        float z = dz * t;

        rope.addVertex(matrix, x - sideX, y + width - yOffset, z + sideZ).setColor(r, g, b, 1.0F).setLight(packedLight);
        rope.addVertex(matrix, x + sideX, y + yOffset, z - sideZ).setColor(r, g, b, 1.0F).setLight(packedLight);
    }
}
