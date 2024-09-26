package com.thebeyond.client.particle;

import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GlopParticle extends TextureSheetParticle {
    public GlopParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet) {
        super(level, x, y, z);
        this.setSprite(spriteSet.get(this.random.nextInt(4), 4));
        this.lifetime = (int) (16.0 / (Math.random() * 0.8 + 0.2));
    }

    @Override
    public void tick() {
        super.tick();
        EnderglopEntity nearestGlop = this.level.getNearestEntity(EnderglopEntity.class, TargetingConditions.DEFAULT, null, this.x, this.y, this.z,
                        new AABB(this.x+10, this.y+10, this.z+10, this.x-10, this.y-10, this.z-10));
        if (nearestGlop != null) {
            Vec3 nearestGlopPos = nearestGlop.position();
            this.move((nearestGlopPos.x - this.x) / 20, (nearestGlopPos.y - this.y) / 20, (nearestGlopPos.z - this.z) / 20);
        }

    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
