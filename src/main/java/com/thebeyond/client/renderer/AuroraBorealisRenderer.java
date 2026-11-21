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

    // relative chunk offsets for the player's chunk, recomputed on crossing a chunk border
    private static final java.util.List<long[]> activeOffsets = new java.util.ArrayList<>();
    private static long activeKey = Long.MIN_VALUE;
    private static int activeRd = -1;

    private static void ensureActiveOffsets(ChunkPos playerChunk, int renderDistance) {
        long key = (((long) playerChunk.x) << 32) ^ (playerChunk.z & 0xFFFFFFFFL);
        if (key == activeKey && renderDistance == activeRd) return;
        activeKey = key;
        activeRd = renderDistance;
        activeOffsets.clear();
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                int cx = playerChunk.x + x;
                int cz = playerChunk.z + z;
                if (cz % 2 == 0) continue;
                if (cx % 2 == 0) continue;
                if ((cx % 4) + 1 == 0) continue;
                if ((cx % 4) - 1 == 0) continue;
                double worldX = (cx << 4) + 8;
                double worldZ = (cz << 4) + 8;
                if (Math.sqrt(worldX * worldX + worldZ * worldZ) < 100) continue;
                activeOffsets.add(new long[]{x, z});
            }
        }
    }

    public static void renderAurora(PoseStack poseStack, float yoffset, float time, MultiBufferSource.BufferSource buffer, RenderLevelStageEvent event, Minecraft mc, Player player, Level level) {
        if (!event.getCamera().getEntity().level().isThundering()) return;
        double rainlevel = event.getCamera().getEntity().level().getThunderLevel(time);

        Frustum frustum = event.getFrustum();
        int renderDistance = mc.options.getEffectiveRenderDistance();
        ChunkPos playerChunk = player.chunkPosition();
        int yLevel = 192;

        ensureActiveOffsets(playerChunk, renderDistance);

        RenderUtils.ResolvedModel mCrumbling = RenderUtils.ResolvedModel.resolve(AURORA_CRUMBLING_MODEL);
        RenderUtils.ResolvedModel m0 = RenderUtils.ResolvedModel.resolve(AURORA_0_MODEL);
        RenderUtils.ResolvedModel m1 = RenderUtils.ResolvedModel.resolve(AURORA_1_MODEL);
        RenderUtils.ResolvedModel m2 = RenderUtils.ResolvedModel.resolve(AURORA_2_MODEL);
        RenderUtils.ResolvedModel m3 = RenderUtils.ResolvedModel.resolve(AURORA_3_MODEL);

        RenderType type = BeyondRenderTypes.cutout();
        var consumer = buffer.getBuffer(type);

        for (long[] off : activeOffsets) {
            int x = (int) off[0];
            int z = (int) off[1];
            {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);

                BlockPos centerPos = new BlockPos(
                        chunkPos.getMiddleBlockX(),
                        yLevel,
                        chunkPos.getMiddleBlockZ()
                );

                float wiggle = Mth.sin((time + (chunkPos.z)*20)/10);
                float wiggle2 = Mth.sin((time + yoffset + (chunkPos.z)*10)/20) + (Mth.sin(chunkPos.z/2f)*15);

                if (frustum.isVisible(tileBounds(centerPos, yoffset, wiggle, wiggle2, rainlevel))) {
                    poseStack.pushPose();
                    applyTileTransform(poseStack, centerPos, yoffset, wiggle, wiggle2, rainlevel);

                    double chunkCenterX = chunkPos.getMiddleBlockX();
                    double chunkCenterZ = chunkPos.getMiddleBlockZ();
                    double deltaX = chunkCenterX - player.getX();
                    double deltaZ = chunkCenterZ - player.getZ();
                    double distanceFromPlayer = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    double maxPlayerDistance = renderDistance * 16 * 0.8;
                    float l = (float) (1.0 - (distanceFromPlayer / maxPlayerDistance));
                    float flash = (Mth.sin(((chunkPos.z+ yoffset)*10) + time/5f) + 1.0f) * 0.5f;
                    int color = (int) (255 * Math.max(l, flash));

                    ResourceLocation modelLoc = getAuroraModel(x, z, chunkPos, renderDistance);
                    RenderUtils.ResolvedModel rm;
                    if (modelLoc == AURORA_CRUMBLING_MODEL) rm = mCrumbling;
                    else if (modelLoc == AURORA_0_MODEL) rm = m0;
                    else if (modelLoc == AURORA_1_MODEL) rm = m1;
                    else if (modelLoc == AURORA_2_MODEL) rm = m2;
                    else rm = m3;
                    rm.emit(poseStack, consumer, type, color, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);

                    poseStack.popPose();
                }
            }
        }
    }

    static void applyTileTransform(PoseStack poseStack, BlockPos centerPos, float yoffset, float wiggle, float wiggle2, double rainlevel) {
        poseStack.translate(centerPos.getX(), centerPos.getY(), centerPos.getZ());
        poseStack.translate(wiggle2, wiggle, 0);
        poseStack.translate(0, yoffset, 0);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        poseStack.scale( 32,32,32);

        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));

        poseStack.translate(0, -0.5f, -0.5f);

        poseStack.translate(0, 0, (1 - rainlevel)*2);
        poseStack.scale((float) rainlevel, (float) rainlevel, (float) rainlevel);
    }

    static AABB tileBounds(BlockPos centerPos, float yoffset, float wiggle, float wiggle2, double rainlevel) {
        double s = 32 * rainlevel;
        double lo = Math.min(0, s), hi = Math.max(0, s);
        double x0 = centerPos.getX() - 0.5 + wiggle2;
        double y0 = centerPos.getY() + yoffset - 0.5 + wiggle + 32 * (2 * (1 - rainlevel) - 0.5);
        double z0 = centerPos.getZ() + 15.5;
        return new AABB(x0 + lo - 0.5, y0 + lo - 0.5, z0 - hi - 0.5, x0 + hi + 0.5, y0 + hi + 0.5, z0 - lo + 0.5);
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
}