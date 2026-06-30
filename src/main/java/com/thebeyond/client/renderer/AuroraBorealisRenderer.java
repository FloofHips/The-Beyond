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

    private static final ResourceLocation AURORA_CRUMBLING_TEX = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_crumbling.png");
    private static final ResourceLocation AURORA_0_TEX = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_0.png");
    private static final ResourceLocation AURORA_1_TEX = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_1.png");
    private static final ResourceLocation AURORA_2_TEX = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_2.png");
    private static final ResourceLocation AURORA_3_TEX = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_3.png");

    // Relative-chunk offsets for the player's chunk; recomputed on chunk-boundary crossing, not per frame.
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

        // Resolve the 5 distinct aurora models ONCE per call instead of per tile.
        RenderUtils.ResolvedModel mCrumbling = RenderUtils.ResolvedModel.resolve(AURORA_CRUMBLING_MODEL);
        RenderUtils.ResolvedModel m0 = RenderUtils.ResolvedModel.resolve(AURORA_0_MODEL);
        RenderUtils.ResolvedModel m1 = RenderUtils.ResolvedModel.resolve(AURORA_1_MODEL);
        RenderUtils.ResolvedModel m2 = RenderUtils.ResolvedModel.resolve(AURORA_2_MODEL);
        RenderUtils.ResolvedModel m3 = RenderUtils.ResolvedModel.resolve(AURORA_3_MODEL);

        var consumer = buffer.getBuffer(BeyondRenderTypes.cutout());

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

                // ~96-block half-extent box bounds the ~32x32 geometry (+ wiggle) so off-screen tiles cull.
                AABB pickaxeAABB = new AABB(
                        centerPos.getX() - 96, centerPos.getY() - 96, centerPos.getZ() - 96,
                        centerPos.getX() + 96, centerPos.getY() + 96, centerPos.getZ() + 96
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

                    poseStack.mulPose(Axis.XP.rotationDegrees(-90f));

                    poseStack.translate(0, -0.5f, -0.5f);

                    poseStack.translate(0, 0, (1 - rainlevel)*2);
                    poseStack.scale((float) rainlevel, (float) rainlevel, (float) rainlevel);

                    double chunkCenterX = chunkPos.getMiddleBlockX();
                    double chunkCenterZ = chunkPos.getMiddleBlockZ();
                    double deltaX = chunkCenterX - player.getX();
                    double deltaZ = chunkCenterZ - player.getZ();
                    double distanceFromPlayer = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    double maxPlayerDistance = renderDistance * 16 * 0.8;
                    float l = (float) (1.0 - (distanceFromPlayer / maxPlayerDistance));
                    float flash = (Mth.sin(((chunkPos.z+ yoffset)*10) + time/5f) + 1.0f) * 0.5f;
                    int color = (int) (255 * Math.max(l, flash));

                    // LOD: a tile whose color rounds to 0 is invisible — skip its transform+emit.
                    if (color <= 0) {
                        poseStack.popPose();
                        continue;
                    }

                    ResourceLocation modelLoc = getAuroraModel(x, z, chunkPos, renderDistance);
                    RenderUtils.ResolvedModel rm;
                    if (modelLoc == AURORA_CRUMBLING_MODEL) rm = mCrumbling;
                    else if (modelLoc == AURORA_0_MODEL) rm = m0;
                    else if (modelLoc == AURORA_1_MODEL) rm = m1;
                    else if (modelLoc == AURORA_2_MODEL) rm = m2;
                    else rm = m3;
                    rm.emit(poseStack, consumer, color, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);

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
            return AURORA_CRUMBLING_TEX;
        }

        if (Math.abs(relZ) == renderDistance-1 || Math.abs(relZ) == renderDistance+1 || Math.abs(relZ) == renderDistance) {
            return AURORA_CRUMBLING_TEX;
        }

        int i = chunkPos.x + chunkPos.z;
        return switch (i % 3) {
            case 0 -> AURORA_0_TEX;
            case 1 -> AURORA_1_TEX;
            case 2 -> AURORA_2_TEX;
            default -> AURORA_3_TEX;
        };
    }
}