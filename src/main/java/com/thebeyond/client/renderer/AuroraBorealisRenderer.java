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

    // the tiles around the player's chunk and what stays fixed between frames, rebuilt on a new chunk or render distance
    private static final java.util.List<Tile> activeTiles = new java.util.ArrayList<>();
    private static long activeKey = Long.MIN_VALUE;
    private static int activeRd = -1;
    private static final float[] LAYER_HEIGHTS = {0, 16};
    private static final int Y_LEVEL = 192;

    private record Tile(BlockPos center, int chunkZ, float sway, int model) {}

    private static void ensureActiveTiles(ChunkPos playerChunk, int renderDistance) {
        long key = (((long) playerChunk.x) << 32) ^ (playerChunk.z & 0xFFFFFFFFL);
        if (key == activeKey && renderDistance == activeRd) return;
        activeKey = key;
        activeRd = renderDistance;
        activeTiles.clear();
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
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                BlockPos center = new BlockPos(chunkPos.getMiddleBlockX(), Y_LEVEL, chunkPos.getMiddleBlockZ());
                int model = modelIndex(getAuroraModel(x, z, chunkPos, renderDistance));
                activeTiles.add(new Tile(center, cz, Mth.sin(cz / 2f) * 15, model));
            }
        }
    }

    public static void renderAurora(PoseStack poseStack, float time, MultiBufferSource.BufferSource buffer, RenderLevelStageEvent event, Minecraft mc, Player player, Level level) {
        if (!event.getCamera().getEntity().level().isThundering()) return;
        double rainlevel = event.getCamera().getEntity().level().getThunderLevel(time);

        Frustum frustum = event.getFrustum();
        int renderDistance = mc.options.getEffectiveRenderDistance();
        ChunkPos playerChunk = player.chunkPosition();

        ensureActiveTiles(playerChunk, renderDistance);

        RenderUtils.ResolvedModel mCrumbling = RenderUtils.ResolvedModel.resolve(AURORA_CRUMBLING_MODEL);
        RenderUtils.ResolvedModel m0 = RenderUtils.ResolvedModel.resolve(AURORA_0_MODEL);
        RenderUtils.ResolvedModel m1 = RenderUtils.ResolvedModel.resolve(AURORA_1_MODEL);
        RenderUtils.ResolvedModel m2 = RenderUtils.ResolvedModel.resolve(AURORA_2_MODEL);
        RenderUtils.ResolvedModel m3 = RenderUtils.ResolvedModel.resolve(AURORA_3_MODEL);

        RenderType type = BeyondRenderTypes.cutout();
        RenderUtils.ResolvedModel[] models = {mCrumbling, m0, m1, m2, m3};
        boolean gpu = AuroraGpuTiles.usable();
        VertexConsumer consumer = gpu ? null : buffer.getBuffer(type);
        if (gpu) AuroraGpuTiles.start();

        for (Tile tile : activeTiles) {
            BlockPos centerPos = tile.center();
            float wiggle = Mth.sin((time + (tile.chunkZ())*20)/10);
            if (!frustum.isVisible(spanBounds(centerPos, wiggle, tile.sway(), rainlevel))) continue;
            for (float yoffset : LAYER_HEIGHTS) {
                float wiggle2 = Mth.sin((time + yoffset + (tile.chunkZ())*10)/20) + tile.sway();

                if (frustum.isVisible(tileBounds(centerPos, yoffset, wiggle, wiggle2, rainlevel))) {
                    double deltaX = centerPos.getX() - player.getX();
                    double deltaZ = centerPos.getZ() - player.getZ();
                    double distanceFromPlayer = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    double maxPlayerDistance = renderDistance * 16 * 0.8;
                    float l = (float) (1.0 - (distanceFromPlayer / maxPlayerDistance));
                    float flash = (Mth.sin(((tile.chunkZ()+ yoffset)*10) + time/5f) + 1.0f) * 0.5f;
                    int color = (int) (255 * Math.max(l, flash));

                    if (gpu) {
                        AuroraGpuTiles.add(tile.model(), poseStack.last().pose(), centerPos, yoffset, wiggle, wiggle2, color);
                    } else {
                        poseStack.pushPose();
                        applyTileTransform(poseStack, centerPos, yoffset, wiggle, wiggle2, rainlevel);
                        models[tile.model()].emit(poseStack, consumer, type, color, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                        poseStack.popPose();
                    }
                }
            }
        }
        if (gpu) AuroraGpuTiles.draw(poseStack, type, rainlevel, models);
    }

    private static int modelIndex(ResourceLocation model) {
        if (model == AURORA_CRUMBLING_MODEL) return 0;
        if (model == AURORA_0_MODEL) return 1;
        if (model == AURORA_1_MODEL) return 2;
        if (model == AURORA_2_MODEL) return 3;
        return 4;
    }

    static void applyTileTransform(PoseStack poseStack, BlockPos centerPos, float yoffset, float wiggle, float wiggle2, double rainlevel) {
        poseStack.translate(centerPos.getX(), centerPos.getY(), centerPos.getZ());
        poseStack.translate(wiggle2, wiggle, 0);
        poseStack.translate(0, yoffset, 0);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        applyTileShape(poseStack, rainlevel);
    }

    static void applyTileShape(PoseStack poseStack, double rainlevel) {
        poseStack.scale( 32,32,32);

        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));

        poseStack.translate(0, -0.5f, -0.5f);

        poseStack.translate(0, 0, (1 - rainlevel)*2);
        poseStack.scale((float) rainlevel, (float) rainlevel, (float) rainlevel);
    }

    // one box around both heights and every sway of a tile, so a tile wholly out of view costs a single frustum test
    static AABB spanBounds(BlockPos centerPos, float wiggle, float sway, double rainlevel) {
        double s = 32 * rainlevel;
        double lo = Math.min(0, s), hi = Math.max(0, s);
        double x0 = centerPos.getX() - 0.5 + sway;
        double y0 = centerPos.getY() - 0.5 + wiggle + 32 * (2 * (1 - rainlevel) - 0.5);
        double z0 = centerPos.getZ() + 15.5;
        return new AABB(x0 + lo - 1.6, y0 + LAYER_HEIGHTS[0] + lo - 0.6, z0 - hi - 0.6,
                x0 + hi + 1.6, y0 + LAYER_HEIGHTS[LAYER_HEIGHTS.length - 1] + hi + 0.6, z0 - lo + 0.6);
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