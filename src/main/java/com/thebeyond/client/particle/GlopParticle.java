package com.thebeyond.client.particle;

import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GlopParticle extends TextureSheetParticle {
    public GlopParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet) {
        super(level, x, y, z);
        this.setSprite(spriteSet.get(this.random.nextInt(4), 4));
        this.lifetime = (int) (64.0 / (Math.random() * 0.8 + 0.2));
    }

    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp(((float)this.age + scaleFactor) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    public void tick() {
        super.tick();
    }
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
}
