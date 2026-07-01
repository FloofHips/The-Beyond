package com.thebeyond.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Smoke puff for the Bellow jet. Lifetime comes from the spawn options so the jet length tracks the redstone-driven
 * reach; with friction off, travel is exactly velocity*lifetime.
 */
public class BellowJetParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected BellowJetParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int lifetime, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = Math.max(1, lifetime);
        this.friction = 1.0F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize *= 3.0F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0F - (float) this.age / (float) (this.lifetime + 1);
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<BellowJetOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(BellowJetOptions options, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new BellowJetParticle(level, x, y, z, vx, vy, vz, options.lifetime(), this.sprites);
        }
    }
}
