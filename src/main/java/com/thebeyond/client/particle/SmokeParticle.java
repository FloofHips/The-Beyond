package com.thebeyond.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

public class SmokeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final Vector3f fromColor;
    private final Vector3f toColor;

    public SmokeParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SmokeColorTransitionOptions options, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;

        //this.quadSize = (float) (options.scale() * 0.1 + level.random.nextFloat() * 0.5);
        this.lifetime = 60 + level.random.nextInt(40);
        this.hasPhysics = true;

        float f = this.random.nextFloat() * 0.4F + 0.6F;
        this.fromColor = this.randomizeColor(options.getFromColor(), f);
        this.toColor = this.randomizeColor(options.getToColor(), f);

        this.xd = xSpeed;
        this.yd = Mth.abs((float) ySpeed);
        this.zd = zSpeed;

        this.scale(options.getScale());

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
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SmokeColorTransitionOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SmokeColorTransitionOptions options, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, this.sprites);
        }
    }
}
