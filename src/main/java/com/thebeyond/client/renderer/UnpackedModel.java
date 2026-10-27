package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** A model's quads decoded once and replayed with putBulkData's arithmetic: the vertices of ItemRenderer.renderModelLists. */
public final class UnpackedModel {
    private static final Map<BakedModel, UnpackedModel> CACHE = new WeakHashMap<>();

    private final String name;
    private final int[] quadStart;
    private final float[] faceNormals;
    private final boolean[] faceNormalUsed;
    private final float[] positions;
    private final float[] uvs;
    private final int[] colors;
    private final int[] bakedBlock;
    private final int[] bakedSky;
    private final int[] normalIndex;
    private final float[] bakedNormals;
    private final Vector3f[] turned;
    private final Vector3f position = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private int verdict;

    private UnpackedModel(String name, List<BakedQuad> quads) {
        this.name = name;
        int vertices = 0;
        for (BakedQuad quad : quads) {
            vertices += quad.getVertices().length / 8;
        }
        this.quadStart = new int[quads.size() + 1];
        this.faceNormals = new float[quads.size() * 3];
        this.faceNormalUsed = new boolean[quads.size()];
        this.positions = new float[vertices * 3];
        this.uvs = new float[vertices * 2];
        this.colors = new int[vertices];
        this.bakedBlock = new int[vertices];
        this.bakedSky = new int[vertices];
        this.normalIndex = new int[vertices];
        List<Float> normals = new ArrayList<>();
        List<Integer> normalKeys = new ArrayList<>();

        // the same buffer reads as putBulkData, which copies each vertex into native-order memory
        ByteBuffer data = ByteBuffer.allocate(DefaultVertexFormat.BLOCK.getVertexSize()).order(ByteOrder.nativeOrder());
        IntBuffer ints = data.asIntBuffer();
        float brightness = 1.0F;
        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;
        float alpha = 1.0F;
        int v = 0;
        for (int q = 0; q < quads.size(); q++) {
            BakedQuad quad = quads.get(q);
            int[] raw = quad.getVertices();
            Vec3i face = quad.getDirection().getNormal();
            this.quadStart[q] = v;
            this.faceNormals[q * 3] = (float) face.getX();
            this.faceNormals[q * 3 + 1] = (float) face.getY();
            this.faceNormals[q * 3 + 2] = (float) face.getZ();
            for (int l = 0; l < raw.length / 8; l++, v++) {
                ints.clear();
                ints.put(raw, l * 8, 8);
                this.positions[v * 3] = data.getFloat(0);
                this.positions[v * 3 + 1] = data.getFloat(4);
                this.positions[v * 3 + 2] = data.getFloat(8);
                float r = (float) (data.get(12) & 255) * brightness * red;
                float g = (float) (data.get(13) & 255) * brightness * green;
                float b = (float) (data.get(14) & 255) * brightness * blue;
                int a = (int) ((alpha * (float) (data.get(15) & 255) / 255.0F) * 255);
                this.colors[v] = FastColor.ARGB32.color(a, (int) r, (int) g, (int) b);
                int light = IQuadTransformer.UV2 * 4;
                this.bakedBlock[v] = Short.toUnsignedInt(data.getShort(light));
                this.bakedSky[v] = Short.toUnsignedInt(data.getShort(light + 2));
                this.uvs[v * 2] = data.getFloat(16);
                this.uvs[v * 2 + 1] = data.getFloat(20);
                byte nx = data.get(28);
                byte ny = data.get(29);
                byte nz = data.get(30);
                if (nx != 0 || ny != 0 || nz != 0) {
                    int key = (nx & 255) | (ny & 255) << 8 | (nz & 255) << 16;
                    int index = normalKeys.indexOf(key);
                    if (index < 0) {
                        index = normalKeys.size();
                        normalKeys.add(key);
                        normals.add(nx / 127f);
                        normals.add(ny / 127f);
                        normals.add(nz / 127f);
                    }
                    this.normalIndex[v] = index;
                } else {
                    this.normalIndex[v] = -1;
                }
            }
            this.faceNormalUsed[q] = this.quadStart[q] < v && this.normalIndex[this.quadStart[q]] < 0;
        }
        this.quadStart[quads.size()] = v;
        this.bakedNormals = new float[normals.size()];
        for (int i = 0; i < normals.size(); i++) {
            this.bakedNormals[i] = normals.get(i);
        }
        this.turned = new Vector3f[normalKeys.size()];
        for (int i = 0; i < this.turned.length; i++) {
            this.turned[i] = new Vector3f();
        }
    }

    /** This pass's replay into this consumer, or null where the item renderer must run: varying quads or a failed byte check. */
    public static @Nullable UnpackedModel replayFor(BakedModel pass, ResourceLocation name, VertexConsumer consumer,
                                                    RenderType type, ItemRenderer renderer, PoseStack poseStack,
                                                    int packedLight, int packedOverlay) {
        if (pass.getClass() != SimpleBakedModel.class || consumer.getClass() != BufferBuilder.class) {
            return null;
        }
        UnpackedModel model = CACHE.get(pass);
        if (model == null) {
            model = new UnpackedModel(name.toString(), quadsOf(pass));
            CACHE.put(pass, model);
        }
        if (model.verdict == 0) {
            model.verdict = model.matchesItemRenderer(pass, type, renderer, poseStack, packedLight, packedOverlay) ? 1 : -1;
            if (model.verdict < 0) {
                TheBeyond.LOGGER.info("[TheBeyond] Model replay off for {}: the item renderer draws it differently here",
                        model.name);
            }
        }
        return model.verdict > 0 ? model : null;
    }

    // the order of ItemRenderer.renderModelLists: each direction, then the unculled quads
    private static List<BakedQuad> quadsOf(BakedModel model) {
        RandomSource random = RandomSource.create();
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            quads.addAll(model.getQuads(null, direction, random));
        }
        random.setSeed(42L);
        quads.addAll(model.getQuads(null, null, random));
        return quads;
    }

    public void emit(PoseStack.Pose pose, VertexConsumer out, int packedLight, int packedOverlay) {
        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        for (int i = 0; i < this.turned.length; i++) {
            this.turned[i].set(this.bakedNormals[i * 3], this.bakedNormals[i * 3 + 1], this.bakedNormals[i * 3 + 2])
                    .mul(normalMatrix);
        }
        int block = packedLight & 0xFFFF;
        int sky = (packedLight >> 16) & 0xFFFF;
        Vector3f at = this.position;
        Vector3f facing = this.normal;
        for (int q = 0; q + 1 < this.quadStart.length; q++) {
            if (this.faceNormalUsed[q]) {
                pose.transformNormal(this.faceNormals[q * 3], this.faceNormals[q * 3 + 1], this.faceNormals[q * 3 + 2], facing);
            }
            for (int v = this.quadStart[q]; v < this.quadStart[q + 1]; v++) {
                int index = this.normalIndex[v];
                if (index >= 0) {
                    facing.set(this.turned[index]);
                }
                matrix.transformPosition(this.positions[v * 3], this.positions[v * 3 + 1], this.positions[v * 3 + 2], at);
                int light = Math.max(block, this.bakedBlock[v]) | Math.max(sky, this.bakedSky[v]) << 16;
                out.addVertex(at.x(), at.y(), at.z(), this.colors[v], this.uvs[v * 2], this.uvs[v * 2 + 1], packedOverlay, light,
                        facing.x(), facing.y(), facing.z());
            }
        }
    }

    // the caller's pose plus a skewed one, whose normals take the normalising branch of transformNormal
    private boolean matchesItemRenderer(BakedModel pass, RenderType type, ItemRenderer renderer, PoseStack poseStack,
                                        int packedLight, int packedOverlay) {
        PoseStack skewed = new PoseStack();
        skewed.translate(3.25F, -1.5F, 7.0F);
        skewed.mulPose(Axis.YP.rotationDegrees(37.0F));
        skewed.scale(1.5F, 0.75F, 2.25F);
        return this.sameBytes(pass, type, renderer, poseStack, packedLight, packedOverlay)
                && this.sameBytes(pass, type, renderer, skewed, 0x00F000F0, packedOverlay);
    }

    private boolean sameBytes(BakedModel pass, RenderType type, ItemRenderer renderer, PoseStack poseStack, int packedLight,
                              int packedOverlay) {
        int size = Math.max(256, (this.quadStart[this.quadStart.length - 1] + 4) * type.format().getVertexSize());
        try (ByteBufferBuilder itemBytes = new ByteBufferBuilder(size);
             ByteBufferBuilder replayBytes = new ByteBufferBuilder(size)) {
            BufferBuilder viaItemRenderer = new BufferBuilder(itemBytes, type.mode(), type.format());
            BufferBuilder viaReplay = new BufferBuilder(replayBytes, type.mode(), type.format());
            renderer.renderModelLists(pass, ItemStack.EMPTY, packedLight, packedOverlay, poseStack, viaItemRenderer);
            this.emit(poseStack.last(), viaReplay, packedLight, packedOverlay);
            try (MeshData expected = viaItemRenderer.build(); MeshData actual = viaReplay.build()) {
                if (expected == null || actual == null) {
                    return expected == actual;
                }
                return sameAttributes(expected.vertexBuffer(), actual.vertexBuffer(), type.format());
            }
        }
    }

    // padding bytes (BLOCK ends with one) are never written and keep whatever the native memory held, so they are skipped
    private static boolean sameAttributes(ByteBuffer expected, ByteBuffer actual, VertexFormat format) {
        if (expected.remaining() != actual.remaining()) {
            return false;
        }
        int size = format.getVertexSize();
        boolean[] written = new boolean[size];
        int[] offsets = format.getOffsetsByElement();
        for (VertexFormatElement element : format.getElements()) {
            for (int k = 0; k < element.byteSize(); k++) {
                written[offsets[element.id()] + k] = true;
            }
        }
        for (int i = 0; i < expected.remaining(); i++) {
            if (written[i % size] && expected.get(expected.position() + i) != actual.get(actual.position() + i)) {
                return false;
            }
        }
        return true;
    }
}
