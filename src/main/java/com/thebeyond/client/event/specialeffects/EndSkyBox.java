package com.thebeyond.client.event.specialeffects;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class EndSkyBox {
    EndSkyBox(){

    }
    private static final int SKY_GRADIENT_LAYERS = 16;
    private static final float SKY_RADIUS = 100.0F;

    // Custom sky colors (RGBA)
    private static final Vector4f HORIZON_COLOR = new Vector4f(0.8f, 0.3f, 0.8f, 1.0f); // Blue-ish
    private static final Vector4f BLACK_COLOR = new Vector4f(0.1f, 0.0f, 0.2f, 1.0f); // Black
    private static final Vector4f TS_COLOR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f); // Black

    private static final ResourceLocation SHOOTING_STAR_LOCATION1 = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/environment/shooting_star1.png");
    private static final ResourceLocation SHOOTING_STAR_LOCATION2 = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/environment/shooting_star2.png");
    // ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (isFoggy) {
            return false;
        }

        PoseStack poseStack = new PoseStack();

        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        poseStack.pushPose();
        poseStack.mulPose(modelViewMatrix);

        Tesselator tesselator = Tesselator.getInstance();

        drawTopSkyGradient(poseStack, tesselator, level);
        drawBottomSkyGradient(poseStack, tesselator, level);
        //drawNadir(poseStack);

        this.renderShootingStars(level, poseStack, tesselator, 95, 1f,    100);
        this.renderShootingStars(level, poseStack, tesselator, 95, -.7f,  95);
        this.renderShootingStars(level, poseStack, tesselator, 95, 1.2f,  95);
        this.renderShootingStars(level, poseStack, tesselator, 95, -3.5f, 90);
        this.renderShootingStars(level, poseStack, tesselator, 95, .5f,   90);
        this.renderShootingStars(level, poseStack, tesselator, 95, -.5f,  90);
        this.renderShootingStars(level, poseStack, tesselator, 95, .7f,   90);
        this.renderShootingStars(level, poseStack, tesselator, 95, -1.0f, 90);
        this.renderShootingStars(level, poseStack, tesselator, 95, 1.2f,  90);
        this.renderShootingStars(level, poseStack, tesselator, 95, -.4f,  90);
        this.renderShootingStars(level, poseStack, tesselator, 95, .6f,   95);
        this.renderShootingStars(level, poseStack, tesselator, 95, -.4f,  95);
        this.renderShootingStars(level, poseStack, tesselator, 95, .4f,   100);
        this.renderShootingStars(level, poseStack, tesselator, 95, -.3f,  100);

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        return true;
    }

    private static void drawTopSkyGradient(PoseStack poseStack, Tesselator tesselator, ClientLevel level) {
        RenderSystem.disableCull();
        Matrix4f matrix = poseStack.last().pose();

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos cameraPos = BlockPos.containing(camera.getPosition());
        Biome biome = level.getBiome(cameraPos).value();
        int fogColor = biome.getFogColor();
        float r = ((fogColor >> 16) & 0xFF) / 255.0f;
        float g = ((fogColor >> 8) & 0xFF) / 255.0f;
        float b = (fogColor & 0xFF) / 255.0f;
        Vec3 rgbfogColor = new Vec3(r,g,b);

        //float gamma = Minecraft.getInstance().options.gamma().get().floatValue();
        Vec3 skyColorVector = level.effects().getBrightnessDependentFogColor(rgbfogColor,0);//.multiply(gamma, gamma, gamma);

        //float fogRed = RenderSystem.getShaderFogColor()[0];
        //float fogGreen = RenderSystem.getShaderFogColor()[1];
        //float fogBlue = RenderSystem.getShaderFogColor()[2];

        float radius = SKY_RADIUS;
        Vector4f color = BLACK_COLOR;
        //RenderSystem.setShaderTexture(0, ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png"));
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        bufferbuilder.addVertex(matrix, 0, 50, 0)
                .setColor((float) color.x(), (float) color.y(), (float) color.z(), 1.0f);

        for (int i = 0; i <= 5; ++i) {
            float angle = (float)i * ((float)Math.PI * 2F) / 6F;
            float x = (float)Math.sin(angle) * radius;
            float z = (float)Math.cos(angle) * radius;
            bufferbuilder.addVertex(matrix, x, -10, z)
                    .setColor((float) skyColorVector.x(), (float) skyColorVector.y(), (float) skyColorVector.z(), 1.0f);
        }

        bufferbuilder.addVertex(matrix, 0, -10, radius)
                .setColor((float) skyColorVector.x(), (float) skyColorVector.y(), (float) skyColorVector.z(), 1.0f);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        RenderSystem.enableCull();
    }

    private static void drawBottomSkyGradient(PoseStack poseStack, Tesselator tesselator, ClientLevel level) {
        RenderSystem.disableCull();
        Matrix4f matrix = poseStack.last().pose();

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos cameraPos = BlockPos.containing(camera.getPosition());
        Biome biome = level.getBiome(cameraPos).value();
        int fogColor = biome.getFogColor();
        float r = ((fogColor >> 16) & 0xFF) / 255.0f;
        float g = ((fogColor >> 8) & 0xFF) / 255.0f;
        float b = (fogColor & 0xFF) / 255.0f;
        Vec3 rgbfogColor = new Vec3(r,g,b);
        //float gamma = Minecraft.getInstance().options.gamma().get().floatValue();
        Vec3 skyColorVector = level.effects().getBrightnessDependentFogColor(rgbfogColor,0);//.multiply(gamma, gamma, gamma);

        float radius = SKY_RADIUS;
        Vector4f color = TS_COLOR;
        Vector4f top_color = HORIZON_COLOR;

        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        bufferbuilder.addVertex(matrix, 0, -250, 0)
                .setColor(color.x(), color.y(), color.z(), 0.0f);

        for (int i = 0; i <= 5; ++i) {
            float angle = (float)i * ((float)Math.PI * 2F) / 6F;
            float x = (float)Math.sin(angle) * radius;
            float z = (float)Math.cos(angle) * radius;
            bufferbuilder.addVertex(matrix, x, -10, z)
                    .setColor((float) skyColorVector.x(), (float) skyColorVector.y(), (float) skyColorVector.z(), 1.0f);
        }

        bufferbuilder.addVertex(matrix, 0, -10, radius)
                .setColor((float) skyColorVector.x(), (float) skyColorVector.y(), (float) skyColorVector.z(), 1.0f);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        RenderSystem.enableCull();
    }

    private static void drawNadir(PoseStack poseStack) {
        RenderSystem.disableCull();
        Tesselator tesselator = Tesselator.getInstance();
        Matrix4f matrix = poseStack.last().pose();

        float radius = SKY_RADIUS;
        Vector4f color = new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);;

        //RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderTexture(0, ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png"));
        RenderSystem.setShader(GameRenderer::getRendertypeEndPortalShader);

        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX_COLOR);

        bufferbuilder.addVertex(matrix, 0, -250, 0)
                .setUv(0.5f, 0.5f)
                .setColor(color.x(), color.y(), color.z(), 1.0f);

        for (int i = 0; i <= 6; ++i) {
            float angle = (float)i * ((float)Math.PI * 2F) / 6F;
            float x = (float)Math.sin(angle) * radius;
            float z = (float)Math.cos(angle) * radius;

            float u = (float)Math.sin(angle) * 0.5f + 0.5f;
            float v = (float)Math.cos(angle) * 0.5f + 0.5f;

            bufferbuilder.addVertex(matrix, x, -220, z)
                    .setUv(u, v)
                    .setColor(color.x(), color.y(), color.z(), 0.0f);
        }

        float firstAngle = 0;
        float firstX = (float)Math.sin(firstAngle) * radius;
        float firstZ = (float)Math.cos(firstAngle) * radius;

        bufferbuilder.addVertex(matrix, firstX, -220, firstZ)
                .setUv(0.0f, 0.5f)
                .setColor(color.x(), color.y(), color.z(), 0.0f);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        RenderSystem.enableCull();
    }

    private void renderShootingStars(ClientLevel level, PoseStack poseStack, Tesselator tesselator, float z, float speed, int size) {
        if (level == null)
            return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos cameraPos = BlockPos.containing(camera.getPosition());
        Biome biome = level.getBiome(cameraPos).value();
        int fogColor = biome.getFogColor();
        float r = ((fogColor >> 16) & 0xFF) / 255.0f;
        float g = ((fogColor >> 8) & 0xFF) / 255.0f;
        float b = (fogColor & 0xFF) / 255.0f;
        Vec3 rgbfogColor = new Vec3(r,g,b);
        Vec3 skyColorVector = level.effects().getBrightnessDependentFogColor(rgbfogColor,0);

        float fogRed = RenderSystem.getShaderFogColor()[0];
        float fogGreen = RenderSystem.getShaderFogColor()[1];
        float fogBlue = RenderSystem.getShaderFogColor()[2];

        poseStack.pushPose();
        RenderSystem.setShaderColor((float) skyColorVector.x, (float) skyColorVector.y, (float) skyColorVector.z, 0.5f);
        //poseStack.mulPose(Axis.XP.rotationDegrees(z));
        poseStack.mulPose(Axis.YP.rotationDegrees((float)level.getGameTime() * .1f * speed));
        poseStack.mulPose(Axis.ZP.rotationDegrees(z));
        Matrix4f matrix4f3 = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        RenderSystem.setShaderTexture(0, (speed > 0 ? SHOOTING_STAR_LOCATION1 : SHOOTING_STAR_LOCATION2));

        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        bufferbuilder.addVertex(matrix4f3, -size, 100.0f, -size).setColor(r,g,b,1).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix4f3, size, 100.0f, -size).setColor(r,g,b,1).setUv(1.0f, 0.0f);
        bufferbuilder.addVertex(matrix4f3, size, 100.0f, size).setColor(r,g,b,1).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix4f3, -size, 100.0f, size).setColor(r,g,b,1).setUv(0.0f, 1.0f);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        //RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        poseStack.popPose();
    }

    //private static void drawCelestialObjects(ClientLevel level, float ticks, float partialTick, PoseStack poseStack) {
    //    // Draw sun/moon/stars similar to vanilla
    //    float sunAngle = level.getTimeOfDay(partialTick);
    //    float sunX = (float)Math.cos(sunAngle * Math.PI * 2.0) * 20.0F;
    //    float sunY = (float)Math.sin(sunAngle * Math.PI * 2.0) * 20.0F;
    //    float sunZ = -10.0F;
////
    //    RenderSystem.setShaderTexture(0, SUN_TEXTURE);
    //    RenderSystem.setShader(GameRenderer::getPositionTexShader);
////
    //    poseStack.pushPose();
    //    poseStack.mulPose(Axis.YP.rotationDegrees(sunAngle * 360.0F));
    //    poseStack.translate(sunX, sunY, sunZ);
////
    //    Matrix4f matrix = poseStack.last().pose();
    //    BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
    //    bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
////
    //    bufferBuilder.vertex(matrix, -5.0F, -5.0F, 0.0F).uv(0.0F, 0.0F).endVertex();
    //    bufferBuilder.vertex(matrix, 5.0F, -5.0F, 0.0F).uv(1.0F, 0.0F).endVertex();
    //    bufferBuilder.vertex(matrix, 5.0F, 5.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
    //    bufferBuilder.vertex(matrix, -5.0F, 5.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
////
    //    BufferUploader.drawWithShader(bufferBuilder.end());
    //    poseStack.popPose();
    //}
}

