package com.thebeyond.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class AuroraciteStepParticle extends TextureSheetParticle {
    public AuroraciteStepParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet) {
        super(level, x, y, z);

        this.lifetime = 50;
        this.quadSize*= 0.5F;
        this.setSpriteFromAge(spriteSet);
        this.alpha = 0;
    }
    @Override
    public void tick() {
        super.tick();

        float f = ((float)this.age / (float)this.lifetime);
        this.quadSize+=0.01f;
        this.y+=0.01f;
        this.rCol = Mth.lerp(f, 1, 0.14f);
        this.gCol = Mth.lerp(f, 1, 0.71f);
        this.bCol = Mth.lerp(f, 1, 0.78f);

        if (f <= 0.2f) {
            this.alpha = f * 5f;
        } else {
            this.alpha = 1f - ((f - 0.2f) / 0.8f);
        }
    }
    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        return new AABB(this.x - 2, this.y - 2, this.z - 2, this.x + 2, this.y + 2, this.z + 2);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        Vec3 cameraPos = renderInfo.getPosition();
        float x = (float)(Mth.lerp(partialTicks, this.xo, this.x) - cameraPos.x());
        float y = (float)(Mth.lerp(partialTicks, this.yo, this.y) - cameraPos.y());
        float z = (float)(Mth.lerp(partialTicks, this.zo, this.z) - cameraPos.z());

        Quaternionf quaternion;
        quaternion = new Quaternionf().rotationYXZ(0, 0, 0);

        this.renderSquare(buffer, x, y, z, partialTicks, quaternion);
    }

    private void renderSquare(VertexConsumer buffer, float x, float y, float z, float partialTicks, Quaternionf rotation) {
        float f1 = this.getU0();
        float f2 = this.getU1();
        float f3 = this.getV0();
        float f4 = this.getV1();

        int i = this.getLightColor(partialTicks);

        this.renderVertex(buffer, x, y, z,  1.0F, -1.0F, f2, f4, i);
        this.renderVertex(buffer, x, y, z,  1.0F,  1.0F, f2, f3, i);
        this.renderVertex(buffer, x, y, z, -1.0F,  1.0F, f1, f3, i);
        this.renderVertex(buffer, x, y, z, -1.0F, -1.0F, f1, f4, i);
    }

    private void renderVertex(VertexConsumer buffer, float x, float y, float z, float xOffset, float yOffset, float u, float v, int packedLight) {
        Vector3f vector3f = (new Vector3f(xOffset, yOffset, 0.0F)).rotate(new Quaternionf().rotationX(-Mth.HALF_PI)).mul(quadSize).add(x, y, z);
        buffer.addVertex(vector3f.x(), vector3f.y(), vector3f.z()).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(packedLight);
    }
}