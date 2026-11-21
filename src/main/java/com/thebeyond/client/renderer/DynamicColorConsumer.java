package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.item.components.Components;
import net.minecraft.client.renderer.block.model.BakedQuad;

/** Not a Sodium VertexBufferWriter, so Sodium's item fast path falls back to renderQuadList and the color still applies. */
public final class DynamicColorConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float red, green, blue, alpha;
    private final int light;

    public DynamicColorConsumer(VertexConsumer delegate, Components.DynamicColorComponent color) {
        this.delegate = delegate;
        float time = (float) Math.sin(System.currentTimeMillis() * 0.001);
        this.red = color.red() + time * color.roffset();
        this.green = color.green() + time * color.goffset();
        this.blue = color.blue() + time * color.boffset();
        this.alpha = color.alpha() + time * color.aoffset();
        this.light = color.brightness();
    }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a,
                            int[] lightmap, int packedOverlay, boolean readExistingColor) {
        delegate.putBulkData(pose, quad, brightness, red, green, blue, alpha, new int[]{light, light, light, light}, packedOverlay, true);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
