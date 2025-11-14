package com.thebeyond.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.thebeyond.TheBeyond;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class AuroraBorealisRenderer {
    //private static final ResourceLocation AURORA_TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/environment/aurora.png");
    public static final ResourceLocation AURORA_0_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_0");
    public static final ResourceLocation AURORA_1_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_1");
    public static final ResourceLocation AURORA_2_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_2");
    public static final ResourceLocation AURORA_3_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_3");

    private static final ResourceLocation AURORA_TEXTURE = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
    private static final int AURORA_HEIGHT = 200;
    private static final float AURORA_SPEED = 0.01f;

    // Cloud-style rendering state
    private static int prevAuroraX, prevAuroraZ;
    private static boolean generateAurora = true;
    private static VertexBuffer auroraBuffer;

    public static void renderAurora(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, ClientLevel level, Minecraft mc) {
        if (level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        float time = (level.getGameTime() + partialTick) * AURORA_SPEED;
        double cloudX = (cameraPos.x + time * 5.0) / 12.0;
        double cloudZ = cameraPos.z / 12.0 + 0.33000001311302185;

        // Cloud-style coordinate wrapping
        cloudX -= Mth.floor(cloudX / 2048.0) * 2048;
        cloudZ -= Mth.floor(cloudZ / 2048.0) * 2048;

        float f3 = (float)(cloudX - Mth.floor(cloudX));
        float f5 = (float)(cloudZ - Mth.floor(cloudZ));

        int currentX = (int)Math.floor(cloudX);
        int currentZ = (int)Math.floor(cloudZ);

        // Regenerate buffer if needed (cloud-style optimization)
        if (currentX != prevAuroraX || currentZ != prevAuroraZ || generateAurora) {
            prevAuroraX = currentX;
            prevAuroraZ = currentZ;
            generateAurora = false;

            if (auroraBuffer != null) {
                auroraBuffer.close();
            }

            auroraBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            auroraBuffer.bind();
            auroraBuffer.upload(buildAuroraMesh(Tesselator.getInstance(), cloudX, AURORA_HEIGHT, cloudZ));
            VertexBuffer.unbind();
        }

        // Render setup (same as clouds)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, AURORA_TEXTURE);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        poseStack.scale(12.0F, 1.0F, 12.0F);
        poseStack.translate(-f3, 0, -f5);

        if (auroraBuffer != null) {
            auroraBuffer.bind();
            RenderType rendertype = RenderType.clouds();
            rendertype.setupRenderState();
            ShaderInstance shaderinstance = RenderSystem.getShader();
            auroraBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shaderinstance);
            rendertype.clearRenderState();
            VertexBuffer.unbind();
        }

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static MeshData buildAuroraMesh(Tesselator tesselator, double x, double y, double z) {
        float f3 = (float)Mth.floor(x) * 0.00390625F;
        float f4 = (float)Mth.floor(z) * 0.00390625F;

        // Aurora colors (green/blue spectrum with variations)
        float baseR = 0.1f;
        float baseG = 0.7f;
        float baseB = 0.9f;

        // Color variations like cloud shading
        float darkR = baseR * 0.7f;
        float darkG = baseG * 0.7f;
        float darkB = baseB * 0.7f;

        float midR = baseR * 0.8f;
        float midG = baseG * 0.8f;
        float midB = baseB * 0.8f;

        float brightR = baseR * 0.9f;
        float brightG = baseG * 0.9f;
        float brightB = baseB * 0.9f;

        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        float auroraLevel = (float)Math.floor(y / 4.0) * 4.0F;

        // Build aurora voxels using cloud technique
        for(int k = -3; k <= 4; ++k) {
            for(int l = -3; l <= 4; ++l) {
                float chunkX = (float)(k * 8);
                float chunkZ = (float)(l * 8);

                // Aurora-specific: Add vertical waves
                float waveOffset = Mth.sin((chunkX + chunkZ) * 0.1f) * 3.0f;
                float auroraThickness = 8.0f + Mth.cos((chunkX - chunkZ) * 0.2f) * 2.0f;

                // Bottom face (darker)
                if (auroraLevel > -5.0F) {
                    bufferbuilder.addVertex(chunkX + 0.0F, auroraLevel + waveOffset, chunkZ + 8.0F)
                            .setUv((chunkX + 0.0F) * 0.00390625F + f3, (chunkZ + 8.0F) * 0.00390625F + f4)
                            .setColor(darkR, darkG, darkB, 0.6F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                    bufferbuilder.addVertex(chunkX + 8.0F, auroraLevel + waveOffset, chunkZ + 8.0F)
                            .setUv((chunkX + 8.0F) * 0.00390625F + f3, (chunkZ + 8.0F) * 0.00390625F + f4)
                            .setColor(darkR, darkG, darkB, 0.6F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                    bufferbuilder.addVertex(chunkX + 8.0F, auroraLevel + waveOffset, chunkZ + 0.0F)
                            .setUv((chunkX + 8.0F) * 0.00390625F + f3, (chunkZ + 0.0F) * 0.00390625F + f4)
                            .setColor(darkR, darkG, darkB, 0.6F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                    bufferbuilder.addVertex(chunkX + 0.0F, auroraLevel + waveOffset, chunkZ + 0.0F)
                            .setUv((chunkX + 0.0F) * 0.00390625F + f3, (chunkZ + 0.0F) * 0.00390625F + f4)
                            .setColor(darkR, darkG, darkB, 0.6F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                }

                // Top face (brighter, with thickness)
                if (auroraLevel <= 5.0F) {
                    bufferbuilder.addVertex(chunkX + 0.0F, auroraLevel + auroraThickness - 9.765625E-4F, chunkZ + 8.0F)
                            .setUv((chunkX + 0.0F) * 0.00390625F + f3, (chunkZ + 8.0F) * 0.00390625F + f4)
                            .setColor(baseR, baseG, baseB, 0.4F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                    bufferbuilder.addVertex(chunkX + 8.0F, auroraLevel + auroraThickness - 9.765625E-4F, chunkZ + 8.0F)
                            .setUv((chunkX + 8.0F) * 0.00390625F + f3, (chunkZ + 8.0F) * 0.00390625F + f4)
                            .setColor(baseR, baseG, baseB, 0.4F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                    bufferbuilder.addVertex(chunkX + 8.0F, auroraLevel + auroraThickness - 9.765625E-4F, chunkZ + 0.0F)
                            .setUv((chunkX + 8.0F) * 0.00390625F + f3, (chunkZ + 0.0F) * 0.00390625F + f4)
                            .setColor(baseR, baseG, baseB, 0.4F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                    bufferbuilder.addVertex(chunkX + 0.0F, auroraLevel + auroraThickness - 9.765625E-4F, chunkZ + 0.0F)
                            .setUv((chunkX + 0.0F) * 0.00390625F + f3, (chunkZ + 0.0F) * 0.00390625F + f4)
                            .setColor(baseR, baseG, baseB, 0.4F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                }

                // Side faces (like cloud edges but with aurora colors)
                buildAuroraSides(bufferbuilder, chunkX, auroraLevel, chunkZ, auroraThickness, waveOffset, f3, f4, brightR, brightG, brightB, midR, midG, midB);
            }
        }

        return bufferbuilder.buildOrThrow();
    }

    private static void buildAuroraSides(BufferBuilder builder, float x, float y, float z, float thickness, float waveOffset, float uOffset, float vOffset, float brightR, float brightG, float brightB, float midR, float midG, float midB) {
        // X- side
        for(int i = 0; i < 8; ++i) {
            builder.addVertex(x + (float)i + 0.0F, y + waveOffset, z + 8.0F)
                    .setUv((x + (float)i + 0.5F) * 0.00390625F + uOffset, (z + 8.0F) * 0.00390625F + vOffset)
                    .setColor(brightR, brightG, brightB, 0.5F)
                    .setNormal(-1.0F, 0.0F, 0.0F);
            builder.addVertex(x + (float)i + 0.0F, y + thickness, z + 8.0F)
                    .setUv((x + (float)i + 0.5F) * 0.00390625F + uOffset, (z + 8.0F) * 0.00390625F + vOffset)
                    .setColor(brightR, brightG, brightB, 0.5F)
                    .setNormal(-1.0F, 0.0F, 0.0F);
            builder.addVertex(x + (float)i + 0.0F, y + thickness, z + 0.0F)
                    .setUv((x + (float)i + 0.5F) * 0.00390625F + uOffset, (z + 0.0F) * 0.00390625F + vOffset)
                    .setColor(brightR, brightG, brightB, 0.5F)
                    .setNormal(-1.0F, 0.0F, 0.0F);
            builder.addVertex(x + (float)i + 0.0F, y + waveOffset, z + 0.0F)
                    .setUv((x + (float)i + 0.5F) * 0.00390625F + uOffset, (z + 0.0F) * 0.00390625F + vOffset)
                    .setColor(brightR, brightG, brightB, 0.5F)
                    .setNormal(-1.0F, 0.0F, 0.0F);
        }

        // X+ side
        for(int i = 0; i < 8; ++i) {
            builder.addVertex(x + (float)i + 1.0F - 9.765625E-4F, y + waveOffset, z + 8.0F)
                    .setUv((x + (float)i + 0.5F) * 0.00390625F + uOffset, (z + 8.0F) * 0.00390625F + vOffset)
                    .setColor(brightR, brightG, brightB, 0.5F)
                    .setNormal(1.0F, 0.0F, 0.0F);
            builder.addVertex(x + (float)i + 1.0F - 9.765625E-4F, y + thickness, z + 8.0F)
                    .setUv((x + (float)i + 0.5F) * 0.00390625F + uOffset, (z + 8.0F) * 0.00390625F + vOffset)
                    .setColor(brightR, brightG, brightB, 0.5F)
                    .setNormal(1.0F, 0.0F, 0.0F);
            builder.addVertex(x + (float)i + 1.0F - 9.765625E-4F, y + thickness, z + 0.0F)
                    .setUv((x + (float)i + 0.5F) * 0.00390625F + uOffset, (z + 0.0F) * 0.00390625F + vOffset)
                    .setColor(brightR, brightG, brightB, 0.5F)
                    .setNormal(1.0F, 0.0F, 0.0F);
            builder.addVertex(x + (float)i + 1.0F - 9.765625E-4F, y + waveOffset, z + 0.0F)
                    .setUv((x + (float)i + 0.5F) * 0.00390625F + uOffset, (z + 0.0F) * 0.00390625F + vOffset)
                    .setColor(brightR, brightG, brightB, 0.5F)
                    .setNormal(1.0F, 0.0F, 0.0F);
        }

        // Z- side
        for(int i = 0; i < 8; ++i) {
            builder.addVertex(x + 0.0F, y + thickness, z + (float)i + 0.0F)
                    .setUv((x + 0.0F) * 0.00390625F + uOffset, (z + (float)i + 0.5F) * 0.00390625F + vOffset)
                    .setColor(midR, midG, midB, 0.5F)
                    .setNormal(0.0F, 0.0F, -1.0F);
            builder.addVertex(x + 8.0F, y + thickness, z + (float)i + 0.0F)
                    .setUv((x + 8.0F) * 0.00390625F + uOffset, (z + (float)i + 0.5F) * 0.00390625F + vOffset)
                    .setColor(midR, midG, midB, 0.5F)
                    .setNormal(0.0F, 0.0F, -1.0F);
            builder.addVertex(x + 8.0F, y + waveOffset, z + (float)i + 0.0F)
                    .setUv((x + 8.0F) * 0.00390625F + uOffset, (z + (float)i + 0.5F) * 0.00390625F + vOffset)
                    .setColor(midR, midG, midB, 0.5F)
                    .setNormal(0.0F, 0.0F, -1.0F);
            builder.addVertex(x + 0.0F, y + waveOffset, z + (float)i + 0.0F)
                    .setUv((x + 0.0F) * 0.00390625F + uOffset, (z + (float)i + 0.5F) * 0.00390625F + vOffset)
                    .setColor(midR, midG, midB, 0.5F)
                    .setNormal(0.0F, 0.0F, -1.0F);
        }

        // Z+ side
        for(int i = 0; i < 8; ++i) {
            builder.addVertex(x + 0.0F, y + thickness, z + (float)i + 1.0F - 9.765625E-4F)
                    .setUv((x + 0.0F) * 0.00390625F + uOffset, (z + (float)i + 0.5F) * 0.00390625F + vOffset)
                    .setColor(midR, midG, midB, 0.5F)
                    .setNormal(0.0F, 0.0F, 1.0F);
            builder.addVertex(x + 8.0F, y + thickness, z + (float)i + 1.0F - 9.765625E-4F)
                    .setUv((x + 8.0F) * 0.00390625F + uOffset, (z + (float)i + 0.5F) * 0.00390625F + vOffset)
                    .setColor(midR, midG, midB, 0.5F)
                    .setNormal(0.0F, 0.0F, 1.0F);
            builder.addVertex(x + 8.0F, y + waveOffset, z + (float)i + 1.0F - 9.765625E-4F)
                    .setUv((x + 8.0F) * 0.00390625F + uOffset, (z + (float)i + 0.5F) * 0.00390625F + vOffset)
                    .setColor(midR, midG, midB, 0.5F)
                    .setNormal(0.0F, 0.0F, 1.0F);
            builder.addVertex(x + 0.0F, y + waveOffset, z + (float)i + 1.0F - 9.765625E-4F)
                    .setUv((x + 0.0F) * 0.00390625F + uOffset, (z + (float)i + 0.5F) * 0.00390625F + vOffset)
                    .setColor(midR, midG, midB, 0.5F)
                    .setNormal(0.0F, 0.0F, 1.0F);
        }
    }
}