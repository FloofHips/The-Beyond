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
        this.lifetime = (int) (64.0 / (Math.random() * 0.8 + 0.2));
    }

    @Override
    public void tick() {
        super.tick();
        EnderglopEntity nearestGlop = level.getNearestEntity(EnderglopEntity.class, TargetingConditions.forNonCombat(), null, x, y, z,
                        new AABB(x+10, y+10, z+10, x-10, y-10, z-10));
        if (nearestGlop != null) {
            Vec3 nearestGlopPos = nearestGlop.position();
            double xd = nearestGlopPos.x - x;
            double yd = nearestGlopPos.y - y;
            double zd = nearestGlopPos.z - z;

            this.move(xd*age / lifetime, yd*age / lifetime, zd*age / lifetime);
            if (this.getPos().distanceTo(nearestGlopPos) < nearestGlop.getSize()*0.3) this.remove();
        }

    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
