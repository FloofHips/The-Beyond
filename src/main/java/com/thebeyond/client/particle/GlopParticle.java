package com.thebeyond.client.particle;

import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GlopParticle extends TextureSheetParticle {
    private volatile EnderglopEntity target;
    private volatile float targetReach;

    public GlopParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet) {
        super(level, x, y, z);
        this.setSprite(spriteSet.get(this.random.nextInt(4), 4));
        this.lifetime = (int) (64.0 / (Math.random() * 0.8 + 0.2));
        Minecraft.getInstance().execute(() -> {
            EnderglopEntity found = level.getNearestEntity(EnderglopEntity.class, TargetingConditions.forNonCombat(), null, x, y, z,
                    new AABB(x + 10, y + 10, z + 10, x - 10, y - 10, z - 10));
            this.targetReach = found != null ? found.getSize() * 0.3f : 0f;
            this.target = found;
        });
    }

    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp(((float)this.age + scaleFactor) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        EnderglopEntity glop = this.target;
        if (glop == null || glop.isRemoved()) {
            return;
        }
        Vec3 targetPos = glop.position();
        double xd = targetPos.x - x;
        double yd = targetPos.y - y;
        double zd = targetPos.z - z;
        this.move(xd * age / lifetime, yd * age / lifetime, zd * age / lifetime);
        if (this.getPos().distanceTo(targetPos) < this.targetReach) this.remove();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
