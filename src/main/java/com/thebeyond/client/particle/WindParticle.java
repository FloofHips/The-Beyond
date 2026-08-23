package com.thebeyond.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class WindParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected WindParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd=xSpeed;
        this.yd=ySpeed;
        this.zd=zSpeed;
        this.sprites = sprites;
        this.scale(1.5f);
        setLifetime((int) (25 + (xSpeed + ySpeed + zSpeed)*100));
        this.alpha = 0;
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        this.yd = Mth.lerp(0.1, this.yd, this.yd + (random.nextBoolean() ? -0.005 : 0.005));
        if (this.age < 10) {
            this.alpha += 0.1f;
        }
        if (this.age > this.lifetime - 10) {
            this.alpha = 1 - (((float)(this.age - (this.lifetime - 10)) / 10.0f));
        }
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return (quaternion, camera, v) -> quaternion.set(camera.rotation().x, camera.rotation().y, camera.rotation().z, camera.rotation().w);
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        renderTail(buffer, renderInfo, partialTicks);
    }

    private void renderTail(VertexConsumer vc, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();

        float px = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x);
        float py = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y);
        float pz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z);

        float speed = Math.abs(new Vector3f((float)xd*20, (float)yd*20, (float)zd*20).length());

        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(px, py-0.1, pz);

        Quaternionf quaternion = new Quaternionf().rotationTo(
                new Vector3f(1, 0, 0),
                new Vector3f((float)xd, (float)yd, (float)zd).normalize()
        );
        poseStack.mulPose(quaternion);


        poseStack.scale(quadSize*2, quadSize*2, quadSize*2);

        Matrix4f mat = poseStack.last().pose();

        int light = this.getLightColor(partialTick);

        vc.addVertex(mat, -1*speed, -1, 0)
                .setColor(255, 255, 255, 0)
                .setUv(getU0(), getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 0, 1);

        vc.addVertex(mat, -1*speed, 1, 0)
                .setColor(255, 255, 255, 0)
                .setUv(getU0(), getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 0, 1);

        vc.addVertex(mat, 1, 1, 0)
                .setColor(255, 255, 255, (int)(this.alpha*255))
                .setUv(getU1(), getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light+100)
                .setNormal(1, 0, 1);

        vc.addVertex(mat, 1, -1, 0)
                .setColor(255, 255, 255, (int)(this.alpha*255))
                .setUv(getU1(), getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light+100)
                .setNormal(0, 0, 1);



        vc.addVertex(mat, -1*speed, -1, 0)
                .setColor(255, 255, 255, 0)
                .setUv(getU0(), getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 0, 1);

        vc.addVertex(mat, 1, -1, 0)
                .setColor(255, 255, 255, (int)(this.alpha*255))
                .setUv(getU1(), getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(Math.min(light+100, 255))
                .setNormal(0, 0, 1);

        vc.addVertex(mat, 1, 1, 0)
                .setColor(255, 255, 255, (int)(this.alpha*255))
                .setUv(getU1(), getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(Math.min(light+100, 255))
                .setNormal(1, 0, 1);

        vc.addVertex(mat, -1*speed, 1, 0)
                .setColor(255, 255, 255, 0)
                .setUv(getU0(), getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 0, 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z) {
            WindParticle windParticle = new WindParticle(level, x, y, z, 0, 0, 0, this.sprites);
            windParticle.pickSprite(this.sprites);
            return windParticle;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            WindParticle windParticle = new WindParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
            windParticle.pickSprite(this.sprites);
            return windParticle;
        }
    }
}
