package com.thebeyond.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class SoulEscapeParticle extends WindParticle {
    protected SoulEscapeParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
    }

    @Override
    protected int getLifetime(double xSpeed, double ySpeed, double zSpeed) {
        return (int) (25 + (xSpeed + ySpeed + zSpeed) * 50);
    }

    @Override
    public void remove() {
        if (level.random.nextFloat() < 0.1)
            this.level.playLocalSound(x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT,1,1, false);
        super.remove();
    }

    @Override
    protected void move() {
        //float weatherMultiplier = level.isRaining() ? 0.1f : 0.01f;
        //this.yd = Mth.lerp(0.1, this.yd, this.yd + (random.nextBoolean() ? -weatherMultiplier : weatherMultiplier));
        //this.xd = Mth.lerp(0.1, this.xd, this.xd + (random.nextBoolean() ? -weatherMultiplier*0.5f : weatherMultiplier*0.5f));
        //this.zd = Mth.lerp(0.1, this.zd, this.zd + (random.nextBoolean() ? -weatherMultiplier*0.5f : weatherMultiplier*0.5f));
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z) {
            SoulEscapeParticle soulParticle = new SoulEscapeParticle(level, x, y, z, 0, 0, 0, this.sprites);
            soulParticle.pickSprite(this.sprites);
            return soulParticle;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            SoulEscapeParticle soulParticle = new SoulEscapeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
            soulParticle.pickSprite(this.sprites);
            return soulParticle;
        }
    }
}
