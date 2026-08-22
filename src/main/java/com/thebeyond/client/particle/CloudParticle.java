package com.thebeyond.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

public class CloudParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final Vector3f fromColor;
    private final Vector3f toColor;

    public CloudParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CloudColorTransitionOptions options, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;

        this.lifetime = 120 + level.random.nextInt(40);
        this.hasPhysics = true;

        float f = this.random.nextFloat() * 0.2F + 0.6F;
        this.fromColor = this.randomizeColor(options.getFromColor(), f);
        this.toColor = this.randomizeColor(options.getToColor(), f);

        this.xd = xSpeed;
        this.yd = Mth.abs((float) ySpeed);
        this.zd = zSpeed;

        this.scale(options.getScale()*2);

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
        if (age == lifetime/2) for (int i = 0; i < 3; i++) {
            int p = level.random.nextBoolean() ? 1 : -1;
            {
                level.addParticle(new SmokeColorTransitionOptions(
                        new Vector3f(this.rCol, this.gCol, this.bCol),
                        new Vector3f(this.rCol, this.gCol, this.bCol),
                        2
                ), this.x, this.y, this.z, 0.01*i*p, 0.01*i*p, 0.01*i*p);
            }
        }
        if (age%2==0) {
            LivingEntity entity = level.getNearestEntity(LivingEntity.class, TargetingConditions.forNonCombat(), null, x, y, z,
                    getBoundingBox());
            if (entity!=null) {
                for (int i = 0; i < 3; i++) {
                    int p = level.random.nextBoolean() ? 1 : -1;
                    {
                        level.addParticle(new SmokeColorTransitionOptions(
                                new Vector3f(this.rCol, this.gCol, this.bCol),
                                new Vector3f(this.rCol, this.gCol, this.bCol),
                                2 * (1-((float) age / lifetime))
                        ), this.x, this.y, this.z, (0.01*i*p)+(entity.getDeltaMovement().x/3f), (0.01*i*p)+(entity.getDeltaMovement().y/3f), (0.01*i*p)+(entity.getDeltaMovement().z/3f));
                    }
                }
                this.remove();
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<CloudColorTransitionOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(CloudColorTransitionOptions options, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new CloudParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, this.sprites);
        }
    }
}

