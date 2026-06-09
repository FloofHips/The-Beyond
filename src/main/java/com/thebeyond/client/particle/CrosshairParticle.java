package com.thebeyond.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CrosshairParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final Vector3f fromColor;
    private final Vector3f toColor;

    public CrosshairParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CrosshairColorTransitionOptions options, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;

        this.quadSize = options.scale();
        this.lifetime = Math.min((int) (60 * options.scale() * 4f), 80);
        this.gravity = 0;
        this.hasPhysics = false;

        float f = this.random.nextFloat() * 0.4F + 0.6F;
        this.fromColor = this.randomizeColor(options.getFromColor(), f);
        this.toColor = this.randomizeColor(options.getToColor(), f);

        setSpriteFromAge(sprites);
    }

    private Vector3f randomizeColor(Vector3f vector, float multiplier) {
        return new Vector3f(this.randomizeColor(vector.x, multiplier), this.randomizeColor(vector.y(), multiplier), this.randomizeColor(vector.z(), multiplier));
    }

    protected float randomizeColor(float coordMultiplier, float multiplier) {
        return (this.random.nextFloat() * 0.2F + 0.8F) * coordMultiplier * multiplier;
    }
    private void lerpColors(float partialTick) {
        float f = ((float)this.age + partialTick) / ((float)this.lifetime + 1.0F);
        Vector3f vector3f = (new Vector3f(this.fromColor)).lerp(this.toColor, f);
        this.rCol = vector3f.x();
        this.gCol = vector3f.y();
        this.bCol = vector3f.z();
    }

    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        this.lerpColors(partialTicks);
        super.render(buffer, renderInfo, partialTicks);
    }

    @Override
    public void tick() {
        //super.tick();

        if (this.age++ >= this.lifetime) this.remove();
        setSpriteFromAge(sprites);

        if (this.age > this.lifetime - 10) {
            this.alpha = 1 - (((float)(this.age - (this.lifetime - 10)) / 10.0f));
        }
        this.quadSize+=0.05f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z, float partialTicks) {
        float f = this.getQuadSize(partialTicks);
        float f1 = this.getU0();
        float f2 = this.getU1();
        float f3 = this.getV0();
        float f4 = this.getV1();
        int i = 255;
        this.renderVertex(buffer, quaternion, x, y, z, 1.0F, -1.0F, f, f2, f4, i);
        this.renderVertex(buffer, quaternion, x, y, z, 1.0F, 1.0F, f, f2, f3, i);
        this.renderVertex(buffer, quaternion, x, y, z, -1.0F, 1.0F, f, f1, f3, i);
        this.renderVertex(buffer, quaternion, x, y, z, -1.0F, -1.0F, f, f1, f4, i);
    }

    private void renderVertex(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z, float xOffset, float yOffset, float quadSize, float u, float v, int packedLight) {
        Vector3f vector3f = (new Vector3f(xOffset, yOffset, 0.0F)).rotate(quaternion).mul(quadSize).add(x, y, z);
        buffer.addVertex(vector3f.x(), vector3f.y(), vector3f.z()).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(packedLight);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<CrosshairColorTransitionOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(CrosshairColorTransitionOptions options, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new CrosshairParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, this.sprites);
        }
    }
}
