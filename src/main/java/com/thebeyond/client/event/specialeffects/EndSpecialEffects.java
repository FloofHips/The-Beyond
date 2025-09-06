package com.thebeyond.client.event.specialeffects;

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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class EndSpecialEffects extends DimensionSpecialEffects {
    private static final int SKY_GRADIENT_LAYERS = 16;
    private static final float SKY_RADIUS = 100.0F;

    // Custom sky colors (RGBA)
    private static final Vector4f HORIZON_COLOR = new Vector4f(0.8f, 0.3f, 0.8f, 1.0f); // Blue-ish
    private static final Vector4f BLACK_COLOR = new Vector4f(0.1f, 0.0f, 0.2f, 1.0f); // Black
    private static final Vector4f TS_COLOR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f); // Black

    private static final ResourceLocation CLOUD_LOCATION_1 = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/environment/cloud_1.png");
    private static final ResourceLocation CLOUD_LOCATION_2 = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/environment/cloud_2.png");
    private static final ResourceLocation HORIZON_LOCATION = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/environment/horizon.png");
    private static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");

    @javax.annotation.Nullable
    private VertexBuffer starBuffer;

    public EndSpecialEffects() {
        super(Float.NaN, false, SkyType.NORMAL, false, false);
        this.createStars();
    }

    private void createStars() {
        if (this.starBuffer != null) {
            this.starBuffer.close();
        }

        this.starBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.starBuffer.bind();
        this.starBuffer.upload(this.drawStars(Tesselator.getInstance()));
        VertexBuffer.unbind();
    }

    private MeshData drawStars(Tesselator tesselator) {
        RandomSource randomsource = RandomSource.create(10842L);

        float f = 100.0F;
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for(int j = 0; j < 1500; ++j) {
            float f1 = randomsource.nextFloat() * 2.0F - 1.0F;
            float f2 = randomsource.nextFloat() * 2.0F - 0.25F;
            float f3 = randomsource.nextFloat() * 2.0F - 1.0F;
            float f4 = 0.15F + randomsource.nextFloat() * 0.1F;
            float f5 = Mth.lengthSquared(f1, f2, f3);
            if (!(f5 <= 0.010000001F) && !(f5 >= 1.0F)) {
                Vector3f vector3f = (new Vector3f(f1, f2, f3)).normalize(100.0F);
                float f6 = (float)(randomsource.nextDouble() * 3.1415927410125732 * 2.0);
                Quaternionf quaternionf = (new Quaternionf()).rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), vector3f).rotateZ(f6);
                bufferbuilder.addVertex(vector3f.add((new Vector3f(f4, -f4, 0.0F)).rotate(quaternionf)));
                bufferbuilder.addVertex(vector3f.add((new Vector3f(f4, f4, 0.0F)).rotate(quaternionf)));
                bufferbuilder.addVertex(vector3f.add((new Vector3f(-f4, f4, 0.0F)).rotate(quaternionf)));
                bufferbuilder.addVertex(vector3f.add((new Vector3f(-f4, -f4, 0.0F)).rotate(quaternionf)));
            }
        }

        return bufferbuilder.buildOrThrow();
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return biomeFogColor;

        if (level.isThundering()) {
            return biomeFogColor.subtract(0,1 * level.getThunderLevel(0),0);
        } else if (level.isRaining()) {
            return biomeFogColor.subtract(0,0.3 * level.getRainLevel(0),0);
        }
        return biomeFogColor;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return super.getSunriseColor(6000, partialTicks);
    }

    @Override
    public boolean isFoggyAt(int i, int i1) {
        return false;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken,
                                     float blockLightRedFlicker, float skyLight,
                                     int pixelX, int pixelY, Vector3f colors) {

        float rain = level.getRainLevel(partialTicks);
        float thunder = level.getThunderLevel(partialTicks);

        float position = Mth.clamp((float) (((Minecraft.getInstance().player.position().y) - 100) / 100), 0, 1);

        if (thunder > 0) {
            float time = (level.getGameTime() + partialTicks) * 0.05f;

            float red = Mth.clamp(Mth.sin(time) * 0.7f + 0.7f, 0f, 1f);
            float blue = Mth.clamp(Mth.sin(time + Mth.TWO_PI*2f/3f) * 0.8f + 0.5f, 0f, 1f);

            float strength = skyLight * 2;

            if (level.isRaining())
                colors.set(
                        Mth.lerp(thunder, colors.x(), Mth.lerp(position, colors.x() + skyLight,Mth.clamp(colors.x() + 2 * red * strength, 0, 1))),
                        Mth.lerp(thunder, Mth.lerp(rain, Mth.clamp(colors.y() * 1.1f, 0, 1), colors.y() * 0.7f), Mth.lerp(position, colors.y() * 0.7f, colors.y())),
                        Mth.lerp(thunder, colors.z(), Mth.lerp(position, colors.z() + skyLight,Mth.clamp(colors.z() + 2 * blue * strength, 0, 1)))
                    );
            else
                colors.set(
                    Mth.lerp(thunder, colors.x() * 0.9f, Mth.lerp(position, colors.x() + skyLight,Mth.clamp(colors.x() + 2 * red * strength, 0, 1))),
                    Mth.lerp(thunder, Mth.clamp(colors.y() * 1.1f, 0, 1), Mth.lerp(position, colors.y() * 0.7f, colors.y())),
                    Mth.lerp(thunder, colors.z() * 0.9f, Mth.lerp(position, colors.z() + skyLight,Mth.clamp(colors.z() + 2 * blue * strength, 0, 1)))
            );
            return;
        } if (rain > 0) {
            colors.set(
                    colors.x(),
                    Mth.lerp(rain, Mth.clamp(colors.y() * 1.1f, 0, 1), colors.y() * 0.7f),
                    colors.z()
            );
            return;
        }
        colors.set(
                colors.x() * 0.9f,
                Mth.clamp(colors.y() * 1.1f, 0, 1),
                colors.z() * 0.9f
        );
    }

    public Vec3 getBiomeColor(ClientLevel level) {

        //FogRenderer.FogData fogData = new FogRenderer.FogData();

        // This is what Vanilla calls to get blended fog color
        //FogRenderer.setupColor(camera, partialTick, level, Minecraft.getInstance().options.getRenderDistance().get(), fogData);

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BiomeManager biomemanager = level.getBiomeManager();
        Vec3 samplePos = camera.getPosition().subtract(2.0, 2.0, 2.0).scale(0.25);
        return CubicSampler.gaussianSampleVec3(samplePos, (x, y, z) ->
                Vec3.fromRGB24(biomemanager.getNoiseBiomeAtQuart(x, y, z).value().getFogColor())
        );

        //Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        //Vec3 cameraPos = camera.getPosition();
//
        //BiomeManager biomeManager = level.getBiomeManager();
//
        //Vec3 totalColor = Vec3.ZERO;
        //int sampleCount = 0;
//
        //for (int x = -1; x <= 1; x++) {
        //    for (int z = -1; z <= 1; z++) {
        //        BlockPos samplePos = BlockPos.containing(
        //                cameraPos.x + x,
        //                cameraPos.y,
        //                cameraPos.z + z
        //        );
//
        //        Holder<Biome> biomeHolder = biomeManager.getBiome(samplePos);
        //        Biome biome = biomeHolder.value();
        //        int fogColor = biome.getFogColor();
//
        //        float r = ((fogColor >> 16) & 0xFF) / 255.0f;
        //        float g = ((fogColor >> 8) & 0xFF) / 255.0f;
        //        float b = (fogColor & 0xFF) / 255.0f;
//
        //        totalColor = totalColor.add(r, g, b);
        //        sampleCount++;
        //    }
        //}
//
        //Vec3 averageColor = totalColor.multiply(1.0 / sampleCount, 1.0 / sampleCount, 1.0 / sampleCount);
//
        //return getBrightnessDependentFogColor(averageColor, 0);
    }

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
        ////renderEndSky(poseStack, level);
//
        ////drawBottomSkyGradient(poseStack, tesselator, level);
        ////drawNadir(poseStack);
//
        ////if (f10 > 0.0F) {
        RenderSystem.setShaderColor(1, 0.5f, 1, 1);
        ////FogRenderer.setupNoFog();
        this.starBuffer.bind();
        this.starBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, GameRenderer.getPositionShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        ////}
        this.renderHorizon(level, poseStack, tesselator);
//
        this.renderCloud(level, poseStack, tesselator, 1f,    100);
        this.renderCloud(level, poseStack, tesselator, -.7f,  95);
        this.renderCloud(level, poseStack, tesselator, 1.2f,  95);
        this.renderCloud(level, poseStack, tesselator, -3.5f, 90);
        this.renderCloud(level, poseStack, tesselator, .5f,   90);
        this.renderCloud(level, poseStack, tesselator, -.5f,  90);
        this.renderCloud(level, poseStack, tesselator, .7f,   90);
        this.renderCloud(level, poseStack, tesselator, -1.0f, 90);
        this.renderCloud(level, poseStack, tesselator, 1.2f,  90);
        this.renderCloud(level, poseStack, tesselator, -.4f,  90);
        this.renderCloud(level, poseStack, tesselator, .6f,   95);
        this.renderCloud(level, poseStack, tesselator, -.4f,  95);
        this.renderCloud(level, poseStack, tesselator, .4f,   100);
        this.renderCloud(level, poseStack, tesselator, -.3f,  100);

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        return true;
    }

    private void renderEndSky(PoseStack poseStack, ClientLevel level) {

        //RenderSystem.enableBlend();
        //RenderSystem.depthMask(false);

        Vec3 skyColor = getBiomeColor(level);
        float alpha = 0.2f;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, END_SKY_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();
        for(int i = 0; i < 6; ++i) {
            poseStack.pushPose();
            if (i == 0) continue;
            Matrix4f matrix4f = poseStack.last().pose();
            BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            if (i == 3) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0F));
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
            }

            if (i == 1) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 0.0f);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 0.0f);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
            }

            if (i == 2) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 0.0f);
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 0.0f);
            }


            if (i == 4) {
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0F));
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 0.0f);
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 0.0f);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
            }

            if (i == 5) {
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90.0F));
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, alpha);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 0.0f);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 0.0f);
            }

            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
            poseStack.popPose();
        }

        RenderSystem.defaultBlendFunc();
        //RenderSystem.depthMask(true);
        //RenderSystem.disableBlend();
    }

    private void drawTopSkyGradient(PoseStack poseStack, Tesselator tesselator, ClientLevel level) {
        RenderSystem.disableCull();
        Matrix4f matrix = poseStack.last().pose();

        float offset = (float) Minecraft.getInstance().cameraEntity.position().y;
        float offsetReal = 40 - ((Mth.clamp(offset, 20, 60)) - 20);
        Vector4f skyColorVector = TS_COLOR;

        float radius = SKY_RADIUS;
        Vector4f color = BLACK_COLOR;
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        bufferbuilder.addVertex(matrix, 0, 50, 0)
                .setColor((float) color.x(), (float) color.y(), (float) color.z(), 1.0f);

        for (int i = 0; i <= 5; ++i) {
            float angle = (float)i * ((float)Math.PI * 2F) / 6F;
            float x = (float)Math.sin(angle) * radius;
            float z = (float)Math.cos(angle) * radius;
            bufferbuilder.addVertex(matrix, x, -50 + offsetReal, z)
                    .setColor((float) skyColorVector.x(), (float) skyColorVector.y(), (float) skyColorVector.z(), 0.0f);
        }

        bufferbuilder.addVertex(matrix, 0, -50 + offsetReal, radius)
                .setColor((float) skyColorVector.x(), (float) skyColorVector.y(), (float) skyColorVector.z(), 0.0f);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        RenderSystem.enableCull();
    }

    private void drawBottomSkyGradient(PoseStack poseStack, Tesselator tesselator, ClientLevel level) {
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
        //Vec3 skyColorVector = level.effects().getBrightnessDependentFogColor(rgbfogColor,0);//.multiply(gamma, gamma, gamma);
        Vector4f skyColorVector = TS_COLOR;

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

    private void drawHorizon(PoseStack poseStack) {
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

    private void renderCloud(ClientLevel level, PoseStack poseStack, Tesselator tesselator, float speed, int size) {
        //stolen from ninni uber

        if (level == null)
            return;

        Vec3 skyColorVector = getBiomeColor(level);

        float fogRed = RenderSystem.getShaderFogColor()[0];
        float fogGreen = RenderSystem.getShaderFogColor()[1];
        float fogBlue = RenderSystem.getShaderFogColor()[2];

        poseStack.pushPose();
        RenderSystem.setShaderColor((float) skyColorVector.x, (float) skyColorVector.y, (float) skyColorVector.z, 0.5f);
        //poseStack.mulPose(Axis.XP.rotationDegrees(z));
        poseStack.mulPose(Axis.YP.rotationDegrees((float)level.getGameTime() * .1f * speed));
        poseStack.mulPose(Axis.ZP.rotationDegrees(95));
        Matrix4f matrix4f3 = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        RenderSystem.setShaderTexture(0, (speed > 0 ? CLOUD_LOCATION_1 : CLOUD_LOCATION_2));

        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        bufferbuilder.addVertex(matrix4f3, -size, 100.0f, -size).setColor(1,1,1,1).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix4f3, size, 100.0f, -size).setColor(1,1,1,1).setUv(1.0f, 0.0f);
        bufferbuilder.addVertex(matrix4f3, size, 100.0f, size).setColor(1,1,1,1).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix4f3, -size, 100.0f, size).setColor(1,1,1,1).setUv(0.0f, 1.0f);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        //RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        poseStack.popPose();
    }

    private void renderHorizon(ClientLevel level, PoseStack poseStack, Tesselator tesselator) {
        //stolen from ninni uber

        if (level == null)
            return;

        Vec3 skyColorVector = getBiomeColor(level);

        float fogRed = RenderSystem.getShaderFogColor()[0];
        float fogGreen = RenderSystem.getShaderFogColor()[1];
        float fogBlue = RenderSystem.getShaderFogColor()[2];

        poseStack.pushPose();
        RenderSystem.setShaderColor((float) skyColorVector.x, (float) skyColorVector.y, (float) skyColorVector.z, 0.5f);
        //RenderSystem.setShaderColor(1, 1, 1, 0.5f);

        poseStack.mulPose(Axis.YP.rotationDegrees(-90));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90));

        Matrix4f matrix4f3 = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        RenderSystem.setShaderTexture(0, HORIZON_LOCATION);

        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        bufferbuilder.addVertex(matrix4f3, -2, 100.0f, -2).setColor(1,1,1,1).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix4f3, 2, 100.0f, -2).setColor(1,1,1,1).setUv(1.0f, 0.0f);
        bufferbuilder.addVertex(matrix4f3, 2, 100.0f, 2).setColor(1,1,1,1).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix4f3, -2, 100.0f, 2).setColor(1,1,1,1).setUv(0.0f, 1.0f);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        //RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        poseStack.popPose();
    }

    //private static void drawCelestialObjects(ClientLevel level, float ticks, float partialTick, PoseStack poseStack) {

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