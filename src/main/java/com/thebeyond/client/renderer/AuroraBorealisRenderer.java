package com.thebeyond.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondRenderTypes;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public class AuroraBorealisRenderer {
    public static final ResourceLocation AURORA_0_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_0");
    public static final ResourceLocation AURORA_1_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_1");
    public static final ResourceLocation AURORA_2_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_2");
    public static final ResourceLocation AURORA_3_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_3");
    public static final ResourceLocation AURORA_CRUMBLING_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_crumbling");

    public static void renderAurora(PoseStack poseStack, float yoffset, float time, MultiBufferSource.BufferSource buffer, RenderLevelStageEvent event, Minecraft mc, Player player, Level level) {
        if (!event.getCamera().getEntity().level().isRaining()) return;

        Frustum frustum = event.getFrustum();
        int renderDistance = mc.options.getEffectiveRenderDistance();
        ChunkPos playerChunk = player.chunkPosition();
        int yLevel = 192;

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                if (chunkPos.z % 2 == 0) continue;
                if (chunkPos.x % 2 == 0) continue;
                if ((chunkPos.x % (4))+1 == 0) continue;
                if ((chunkPos.x % (4))-1 == 0) continue;
                double worldX = chunkPos.getMiddleBlockX();
                double worldZ = chunkPos.getMiddleBlockZ();
                double distanceFromCenter = Math.sqrt(worldX * worldX + worldZ * worldZ);
                if (distanceFromCenter < 100) continue;

                double distance = Math.sqrt(chunkPos.x * chunkPos.x + chunkPos.z * chunkPos.z);
                double maxDistance = renderDistance * Math.sqrt(2);
                float alpha = (float) (distance*2);

                BlockPos centerPos = new BlockPos(
                        chunkPos.getMiddleBlockX(),
                        yLevel,
                        chunkPos.getMiddleBlockZ()
                );

                AABB pickaxeAABB = new AABB(
                        centerPos.getX() - 128, centerPos.getY() - 128, centerPos.getZ() - 128,
                        centerPos.getX() + 128, centerPos.getY() + 128, centerPos.getZ() + 128
                );

                if (frustum.isVisible(pickaxeAABB)) {
                    poseStack.pushPose();

                    poseStack.translate(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                    float wiggle = Mth.sin((time + (chunkPos.z)*20)/10);
                    float wiggle2 = Mth.sin((time + yoffset + (chunkPos.z)*10)/20) + (Mth.sin(chunkPos.z/2f)*15);

                    poseStack.translate(wiggle2, wiggle, 0);
                    poseStack.translate(0, yoffset, 0);
                    poseStack.translate(-0.5f, -0.5f, -0.5f);
                    poseStack.scale( 32,32,32);
                    //poseStack.translate(4, 0, 4);

                    poseStack.mulPose(Axis.XP.rotationDegrees(-90f));

                    //poseStack.mulPose(Axis.XP.rotationDegrees(alpha));
                    poseStack.translate(0, -0.5f, -0.5f);

                    double chunkCenterX = chunkPos.getMiddleBlockX();
                    double chunkCenterZ = chunkPos.getMiddleBlockZ();
                    double deltaX = chunkCenterX - player.getX();
                    double deltaZ = chunkCenterZ - player.getZ();
                    double distanceFromPlayer = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    double maxPlayerDistance = renderDistance * 16 * 0.8;
                    float l = (float) (1.0 - (distanceFromPlayer / maxPlayerDistance));
                    float flash = (Mth.sin(((chunkPos.z+ yoffset)*10) + time/5f) + 1.0f) * 0.5f;
                    int color = (int) (255 * Math.max(l, flash));

                    //RenderUtils.renderModel(getAuroraModel(x, z, chunkPos, renderDistance), poseStack, buffer.getBuffer(BeyondRenderTypes.entityCutout(getAuroraTexture(x, z, chunkPos, renderDistance))), color, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                    RenderUtils.renderModel(getAuroraModel(x, z, chunkPos, renderDistance), poseStack, buffer.getBuffer(BeyondRenderTypes.cutout()), color, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);

                    poseStack.popPose();
                }
            }
        }
    }

    public static ResourceLocation getAuroraModel(int relX, int relZ, ChunkPos chunkPos, int renderDistance) {
        double worldX = chunkPos.getMiddleBlockX();
        double worldZ = chunkPos.getMiddleBlockZ();
        double distanceFromCenter = Math.sqrt(worldX * worldX + worldZ * worldZ);

        if (distanceFromCenter < renderDistance * 16 * 0.7) {
            return AURORA_CRUMBLING_MODEL;
        }

        if (Math.abs(relZ) == renderDistance-1 || Math.abs(relZ) == renderDistance+1 || Math.abs(relZ) == renderDistance) {
            return AURORA_CRUMBLING_MODEL;
        }

        int i = chunkPos.x + chunkPos.z;
        return switch (i % 3) {
            case 0 -> AURORA_0_MODEL;
            case 1 -> AURORA_1_MODEL;
            case 2 -> AURORA_2_MODEL;
            default -> AURORA_3_MODEL;
        };
    }

    public static ResourceLocation getAuroraTexture(int relX, int relZ, ChunkPos chunkPos, int renderDistance) {
        double worldX = chunkPos.getMiddleBlockX();
        double worldZ = chunkPos.getMiddleBlockZ();
        double distanceFromCenter = Math.sqrt(worldX * worldX + worldZ * worldZ);

        if (distanceFromCenter < renderDistance * 16 * 0.7) {
            return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_crumbling.png");
        }

        if (Math.abs(relZ) == renderDistance-1 || Math.abs(relZ) == renderDistance+1 || Math.abs(relZ) == renderDistance) {
            return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_crumbling.png");
        }

        int i = chunkPos.x + chunkPos.z;
        return switch (i % 3) {
            case 0 -> ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_0.png");
            case 1 -> ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_1.png");
            case 2 -> ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_2.png");
            default -> ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_3.png");
        };
    }
}