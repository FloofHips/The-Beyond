package com.thebeyond.client.event.specialeffects;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.ModClientEvents;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
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

    private float bossFog;

    @javax.annotation.Nullable
    private VertexBuffer starBuffer;

    public EndSpecialEffects() {
        super(Float.NaN, false, SkyType.NORMAL, false, false);
        this.createStars();
        this.bossFog = 0;
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
//
        if (level == null) return biomeFogColor;
//
        if (level.isThundering()) {
            return biomeFogColor.subtract(0,1 * level.getThunderLevel(0),0);
        } else if (level.isRaining()) {
            return biomeFogColor.subtract(0,0.3 * level.getRainLevel(0),0);
        }

        return biomeFogColor.multiply(
                Mth.lerp(bossFog, 1, 0.3F),
                Mth.lerp(bossFog, 1, 0.4F),
                Mth.lerp(bossFog, 1, 0.4F));
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
//
        float position = Mth.clamp((float) (((Minecraft.getInstance().player.position().y) - 100) / 100), 0, 1);
//
        if (thunder > 0) {
            float time = (level.getGameTime() + partialTicks) * 0.05f;
//
            float red = Mth.clamp(Mth.sin(time) * 0.7f + 0.7f, 0f, 1f);
            float blue = Mth.clamp(Mth.sin(time + Mth.TWO_PI*2f/3f) * 0.8f + 0.5f, 0f, 1f);
//
            float strength = skyLight * 2;
//
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
        } else if (rain > 0) {
            colors.set(
                    colors.x(),
                    Mth.lerp(rain, Mth.clamp(colors.y() * 1.1f, 0, 1), colors.y() * 0.7f),
                    colors.z()
            );
            return;
        }
        ////i think the skylight is being fucked with by thunderstorms... or ambient light
//
        colors.set(
                Mth.lerp(bossFog, colors.x() * 0.9f, colors.x() * 0.3 + 0.22983016),
                Mth.lerp(bossFog, Mth.clamp(colors.y() * 1.1f, 0, 1), colors.y() * 0.4),
                Mth.lerp(bossFog, colors.z() * 0.9f, colors.z() * 0.4 + 0.22983016)
        );
    }

    public Vec3 getBiomeColor(ClientLevel level) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BiomeManager biomemanager = level.getBiomeManager();
        Vec3 samplePos = camera.getPosition().subtract(2.0, 2.0, 2.0).scale(0.25);
        Vec3 color = CubicSampler.gaussianSampleVec3(samplePos, (x, y, z) -> Vec3.fromRGB24(biomemanager.getNoiseBiomeAtQuart(x, y, z).value().getFogColor()));
        return color.lerp(new Vec3(0,0,0), 1-ModClientEvents.effectFog);
    }

    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (Minecraft.getInstance().gui.getBossOverlay().shouldCreateWorldFog()) {
            bossFog = (float) Mth.clamp(bossFog + 0.005, 0, 1);
        } else {
            bossFog = (float) Mth.clamp(bossFog - 0.005, 0, 1);
        }

        //if (ModClientEvents.effectFog == 0) return false;

        PoseStack poseStack = new PoseStack();

        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        poseStack.pushPose();
        poseStack.mulPose(modelViewMatrix);

        Tesselator tesselator = Tesselator.getInstance();

        drawTopSkyGradient(poseStack, tesselator, level);

        RenderSystem.setShaderColor(1, 0.5f, 1, 1);

        this.starBuffer.bind();
        this.starBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, GameRenderer.getPositionShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        this.renderHorizon(level, poseStack, tesselator);

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

    private void drawTopSkyGradient(PoseStack poseStack, Tesselator tesselator, ClientLevel level) {
        RenderSystem.disableCull();
        Matrix4f matrix = poseStack.last().pose();

        float offset = (float) Minecraft.getInstance().cameraEntity.position().y;
        float offsetReal = 40 - ((Mth.clamp(offset, 20, 60)) - 20);
        Vector4f skyColorVector = getSkycolor(TS_COLOR);

        float radius = SKY_RADIUS;
        Vector4f color = getSkycolor(BLACK_COLOR);
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

    private Vector4f getSkycolor(Vector4f color) {
        return new Vector4f(
                Mth.lerp(ModClientEvents.effectFog, 0, color.x),
                Mth.lerp(ModClientEvents.effectFog, 0, color.y),
                Mth.lerp(ModClientEvents.effectFog, 0, color.z),
                Mth.lerp(ModClientEvents.effectFog, 1, color.w)
        );
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
}