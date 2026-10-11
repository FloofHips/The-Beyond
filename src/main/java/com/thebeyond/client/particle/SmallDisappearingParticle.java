package com.thebeyond.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class SmallDisappearingParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected SmallDisappearingParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.quadSize = 0.35f;
        setLifetime(20);
    }

    @Override
    public void tick() {
        super.tick();
        if (age>8) scale(0.6f);
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
            SmallDisappearingParticle biteParticle = new SmallDisappearingParticle(level, x, y, z, 0, 0, 0, this.sprites);
            biteParticle.pickSprite(this.sprites);
            return biteParticle;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            SmallDisappearingParticle biteParticle = new SmallDisappearingParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
            biteParticle.pickSprite(this.sprites);
            return biteParticle;
        }
    }
}
