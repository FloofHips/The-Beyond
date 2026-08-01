package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionHandler;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.thebeyond.common.entity.util.livingblock.AABBBuilder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;

public class LivingBlockRenderer extends EntityRenderer<LivingBlock> {

    private static final BlockState BLOCK_STATE = Blocks.RED_CONCRETE.defaultBlockState();
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/red_concrete.png");

    private final BlockRenderDispatcher blockRenderer;

    public LivingBlockRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.4F;
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

        Quaternionf rotation = new Quaternionf();
        entity.getRotation(rotation, partialTicks);

        AABBBuilder builder = new AABBBuilder();
        LivingBlockCollisionHandler.includeRotatedOBBCorners(entity, rotation, builder);

        Direction climbDir  = entity.getClimbingDirection();
        Direction.Axis axis = climbDir.getAxis();
        int step = climbDir.getAxisDirection().getStep();
        double edgeOffset = builder.edge(climbDir);

        double translateX = 0.0;
        double translateY = 0.0;
        double translateZ = 0.0;

        if (axis == Direction.Axis.Y) {
            if (climbDir == Direction.DOWN) {
                translateY = -edgeOffset;
            } else {
                translateY = sizeY - edgeOffset;
            }
        } else {
            translateY = sizeY / 2.0;
            if (axis == Direction.Axis.X) {
                translateX = (sizeX / 2.0) * step - edgeOffset;
            } else {
                translateZ = (sizeZ / 2.0) * step - edgeOffset;
            }
        }

        poseStack.pushPose();
        poseStack.translate(translateX, translateY, translateZ);

        Vector3fc pogo = entity.getPogoScale(partialTicks);
        poseStack.scale(pogo.x(), pogo.y(), pogo.z());
        poseStack.mulPose(rotation);

        AABB bounds = entity.getShapeBounds();
        poseStack.translate(
                -(bounds.minX + bounds.maxX) / 2.0,
                -(bounds.minY + bounds.maxY) / 2.0,
                -(bounds.minZ + bounds.maxZ) / 2.0
        );

        for (AABB subBox : entity.getShapeBoxes()) {
            poseStack.pushPose();
            poseStack.translate(subBox.minX, subBox.minY, subBox.minZ);
            poseStack.scale(
                    (float) subBox.getXsize(),
                    (float) subBox.getYsize(),
                    (float) subBox.getZsize()
            );

            this.blockRenderer.renderSingleBlock(
                    this.getBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

            poseStack.popPose();
        }

        poseStack.popPose();

        Entity leashHolder = entity.getLeashHolder();
        if (leashHolder != null) {
            this.renderLeash(entity, partialTicks, poseStack, buffer, leashHolder);
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    public BlockState getBlockState() {
        return BLOCK_STATE;
    }

    @Override
    public ResourceLocation getTextureLocation(final LivingBlock entity) {
        return TEXTURE;
    }

    private void renderLeash(final LivingBlock entity,
                             final float partialTicks,
                             final PoseStack poseStack,
                             final MultiBufferSource buffer,
                             final Entity holder) {
        poseStack.pushPose();

        Vec3 ropeHold = holder.getRopeHoldPosition(partialTicks);
        double yaw = Mth.lerp(partialTicks, entity.yBodyRotO, entity.yBodyRot) * (Math.PI / 180.0) + (Math.PI / 2.0);
        Vec3 leashOffset = entity.getLeashOffset(partialTicks);
        double offX = Math.cos(yaw) * leashOffset.z + Math.sin(yaw) * leashOffset.x;
        double offZ = Math.sin(yaw) * leashOffset.z - Math.cos(yaw) * leashOffset.x;
        double startX = Mth.lerp((double) partialTicks, entity.xo, entity.getX()) + offX;
        double startY = Mth.lerp((double) partialTicks, entity.yo, entity.getY()) + leashOffset.y;
        double startZ = Mth.lerp((double) partialTicks, entity.zo, entity.getZ()) + offZ;

        poseStack.translate(offX, leashOffset.y, offZ);

        float dx = (float) (ropeHold.x - startX);
        float dy = (float) (ropeHold.y - startY);
        float dz = (float) (ropeHold.z - startZ);

        VertexConsumer rope = buffer.getBuffer(RenderType.leash());
        Matrix4f matrix = poseStack.last().pose();
        float inv = (float) (Mth.invSqrt(dx * dx + dz * dz) * 0.025F / 2.0F);
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

    private static void addLeashVertexPair(final VertexConsumer rope,
                                           final Matrix4f matrix,
                                           final float dx, final float dy, final float dz,
                                           final int entityBlockLight, final int holderBlockLight,
                                           final int entitySkyLight, final int holderSkyLight,
                                           final float width, final float yOffset,
                                           final float sideX, final float sideZ,
                                           final int index, final boolean end) {
        float t = index / 24.0F;
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

        rope.addVertex(matrix, x - sideX, y + width - yOffset, z + sideZ)
                .setColor(r, g, b, 1.0F).setLight(packedLight);
        rope.addVertex(matrix, x + sideX, y + yOffset, z - sideZ)
                .setColor(r, g, b, 1.0F).setLight(packedLight);
    }
}