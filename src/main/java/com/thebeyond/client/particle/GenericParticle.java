package com.thebeyond.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class GenericParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected GenericParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        scale(10);
        setLifetime(15);
    }

    protected GenericParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        scale(10);
        setLifetime(15);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        if (age>8) scale(0.5f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z) {
            GenericParticle genericParticle = new GenericParticle(level, x, y, z, 0, 0, 0, this.sprites);
            genericParticle.pickSprite(this.sprites);
            return genericParticle;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            GenericParticle genericParticle = new GenericParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
            genericParticle.pickSprite(this.sprites);
            return genericParticle;
        }
    }
}
