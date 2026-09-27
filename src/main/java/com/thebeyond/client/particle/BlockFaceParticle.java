package com.thebeyond.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

import java.awt.*;

public class BlockFaceParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    public float xRot = 0;
    public float yRot = 0;
    public float zRot = 0;

    protected BlockFaceParticle(ClientLevel level, double x, double y, double z, double xRot, double yRot, double zRot, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        scale(1);
        setLifetime(20);
        this.xRot = (float) xRot;
        this.yRot = (float) yRot;
        this.zRot = (float) zRot;
        this.quadSize = 0.3f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float f = (float) (this.age) / (this.lifetime);
        //this.quadSize = Mth.lerp(f, 0.3f,0);
        this.alpha = Mth.lerp(f, 1,0);
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        Quaternionf quaternionf = new Quaternionf()
                .rotateY(-this.yRot * ((float)Math.PI / 180F))
                .rotateX(this.xRot * ((float)Math.PI / 180F))
                .rotateZ(this.zRot * ((float)Math.PI / 180F));

        this.renderRotatedQuad(buffer, renderInfo, quaternionf, partialTicks);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ADDITIVE_SHEET;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z) {
            BlockFaceParticle biteParticle = new BlockFaceParticle(level, x, y, z, 0, 0, 0, this.sprites);
            biteParticle.pickSprite(this.sprites);
            return biteParticle;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            BlockFaceParticle biteParticle = new BlockFaceParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
            biteParticle.pickSprite(this.sprites);
            return biteParticle;
        }
    }


    public static final ParticleRenderType ADDITIVE_SHEET = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(true);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "PARTICLE_SHEET_ADDITIVE";
        }

        @Override
        public boolean isTranslucent() {
            return true;
        }
    };
}
