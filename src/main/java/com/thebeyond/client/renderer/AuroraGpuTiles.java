package com.thebeyond.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.thebeyond.TheBeyond;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Builds every storm aurora tile on the GPU in one buffer, drawn the normal way so shader packs get the CPU path's vertices. */
final class AuroraGpuTiles {
    static final int MODELS = 5;

    static boolean disabled;

    private static final float[][] tiles = new float[MODELS][256];
    private static final int[] counts = new int[MODELS];
    private static final int[] firstVertex = new int[MODELS];
    private static final int[] vertices = new int[MODELS];

    // the templates stay on the GPU until the shader's format, the thunder level, the pose's turn or a model changes
    private static VertexFormat templateFormat;
    private static double templateRain = Double.NaN;
    private static final Matrix4f templateTurn = new Matrix4f();
    private static final Matrix3f templateNormal = new Matrix3f();
    private static final List<?>[] templatePasses = new List<?>[MODELS];
    private static final Matrix4f turn = new Matrix4f();
    private static boolean formatWarned;

    private static ByteBufferBuilder templateBytes;
    private static ByteBuffer templateStage;
    private static ByteBuffer tileStage;
    private static int templateBuffer, tileBuffer, tileTexture, outputBuffer, drawVao, feedbackVao, program, tileBaseUniform;
    private static long outputCapacity;
    private static int layoutSize = -1, layoutLightWord = -1;
    private static final Map<ShaderInstance, Integer> checkedSizes = new WeakHashMap<>();
    private static VertexFormat drawFormat;

    private AuroraGpuTiles() {}

    static boolean usable() {
        if (disabled) return false;
        if (!GL.getCapabilities().OpenGL32) {
            disabled = true;
            TheBeyond.LOGGER.info("[TheBeyond] Storm aurora stays on the CPU: this driver lacks OpenGL 3.2");
            return false;
        }
        return true;
    }

    static void start() {
        Arrays.fill(counts, 0);
    }

    // the same four translations as the CPU path, in double so large world coordinates cancel out before rounding to float
    static void add(int model, Matrix4f pose, BlockPos center, float yoffset, float wiggle, float wiggle2, int light) {
        double x = center.getX() + (double) wiggle2 - 0.5;
        double y = center.getY() + (double) wiggle + yoffset - 0.5;
        double z = center.getZ() - 0.5;
        int i = counts[model] * 4;
        if (i + 4 > tiles[model].length) {
            tiles[model] = Arrays.copyOf(tiles[model], tiles[model].length * 2);
        }
        float[] t = tiles[model];
        t[i] = (float) (pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30());
        t[i + 1] = (float) (pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31());
        t[i + 2] = (float) (pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32());
        t[i + 3] = light;
        counts[model]++;
    }

    static void draw(PoseStack poseStack, RenderType type, double rainlevel, RenderUtils.ResolvedModel[] models) {
        try {
            drawTiles(poseStack, type, rainlevel, models);
        } catch (RuntimeException e) {
            disabled = true;
            TheBeyond.LOGGER.warn("[TheBeyond] Storm aurora back on the CPU, its GPU path failed", e);
        }
    }

    private static void drawTiles(PoseStack poseStack, RenderType type, double rainlevel, RenderUtils.ResolvedModel[] models) {
        int total = 0;
        for (int count : counts) total += count;
        if (total == 0) return;
        if (type.mode() != VertexFormat.Mode.QUADS) throw new IllegalStateException("aurora render type is not drawn in quads");
        if (templateBuffer == 0) {
            templateBuffer = GlStateManager._glGenBuffers();
            tileBuffer = GlStateManager._glGenBuffers();
            outputBuffer = GlStateManager._glGenBuffers();
            drawVao = GlStateManager._glGenVertexArrays();
            tileTexture = GL11.glGenTextures();
        }

        type.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        try {
            if (shader == null) throw new IllegalStateException("the aurora render type set no shader");
            if (!templatesFit(shader.getVertexFormat(), poseStack.last(), rainlevel, models)) {
                buildTemplates(poseStack, type, rainlevel, models);
                if (templateFormat != null && !templateFormat.equals(shader.getVertexFormat()) && !formatWarned) {
                    formatWarned = true;
                    TheBeyond.LOGGER.info("[TheBeyond] Storm aurora templates rebuilt every frame: they come out as {} but the shader takes {}",
                            templateFormat, shader.getVertexFormat());
                }
            }
            VertexFormat format = templateFormat;
            if (format == null) return;

            int size = format.getVertexSize();
            int lightOffset = format.contains(VertexFormatElement.UV2) ? format.getOffset(VertexFormatElement.UV2) : -1;
            if (size % 4 != 0 || format.getOffset(VertexFormatElement.POSITION) != 0 || lightOffset < 12 || lightOffset % 4 != 0) {
                throw new IllegalStateException("aurora vertex format " + format + " does not fit the feedback copy");
            }
            boolean check = !Integer.valueOf(size).equals(checkedSizes.get(shader));
            for (int i = 0; check && i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            }

            tileStage = ensure(tileStage, total * 16);
            FloatBuffer tileFloats = tileStage.clear().asFloatBuffer();
            for (int m = 0; m < MODELS; m++) {
                tileFloats.put(tiles[m], 0, counts[m] * 4);
            }
            long outputBytes = 0;
            for (int m = 0; m < MODELS; m++) {
                outputBytes += (long) counts[m] * vertices[m] * size;
            }
            int drawn = (int) (outputBytes / size);
            if (drawn == 0) return;

            if (size != layoutSize || lightOffset / 4 != layoutLightWord) {
                buildProgram(size, lightOffset / 4);
                layoutSize = size;
                layoutLightWord = lightOffset / 4;
            }

            BufferUploader.invalidate();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, tileBuffer);
            GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, tileStage.clear().limit(total * 16), GL15.GL_STREAM_DRAW);
            if (outputBytes > outputCapacity) {
                outputCapacity = outputBytes + outputBytes / 2;
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, outputBuffer);
                GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, outputCapacity, GL15.GL_STREAM_COPY);
            }

            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(),
                    Minecraft.getInstance().getWindow());
            try {
                // before apply, since any program bind clears the tessellation flag Iris sets in apply
                feedback(size, drawn, total, shader.getId(), check);
                shader.apply();
                GlStateManager._glBindVertexArray(drawVao);
                if (drawFormat != format) {
                    GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, outputBuffer);
                    if (drawFormat != null) drawFormat.clearBufferState();
                    format.setupBufferState();
                    drawFormat = format;
                }
                int indexCount = drawn / 4 * 6;
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                indices.bind(indexCount);
                RenderSystem.drawElements(GL11.GL_TRIANGLES, indexCount, indices.type().asGLType);
                if (check) {
                    int error = GL11.glGetError();
                    if (error != GL11.GL_NO_ERROR) {
                        disabled = true;
                        TheBeyond.LOGGER.warn("[TheBeyond] Storm aurora back on the CPU, its GPU draw raised GL error 0x{}",
                                Integer.toHexString(error));
                        return;
                    }
                    checkedSizes.put(shader, size);
                    TheBeyond.LOGGER.info("[TheBeyond] Storm aurora drawn on the GPU: {}-byte vertices, {} tiles, {} vertices from transform feedback",
                            size, total, drawn);
                }
            } finally {
                shader.clear();
            }
        } finally {
            VertexBuffer.unbind();
            type.clearRenderState();
        }
    }

    private static boolean templatesFit(VertexFormat wanted, PoseStack.Pose pose, double rainlevel, RenderUtils.ResolvedModel[] models) {
        if (templateFormat == null || !templateFormat.equals(wanted) || rainlevel != templateRain) return false;
        if (!turn.set(pose.pose()).setTranslation(0, 0, 0).equals(templateTurn) || !pose.normal().equals(templateNormal)) return false;
        for (int m = 0; m < MODELS; m++) {
            if (!models[m].passes().equals(templatePasses[m])) return false;
        }
        return true;
    }

    // each model is built once, turned by the pose but not moved, since the tile offsets carry the translation
    private static void buildTemplates(PoseStack poseStack, RenderType type, double rainlevel, RenderUtils.ResolvedModel[] models) {
        if (templateBytes == null) templateBytes = new ByteBufferBuilder(16384);
        templateFormat = null;
        VertexFormat format = null;
        int templateSize = 0;
        int templateVertices = 0;
        poseStack.pushPose();
        try {
            poseStack.last().pose().setTranslation(0, 0, 0);
            AuroraBorealisRenderer.applyTileShape(poseStack, rainlevel);
            for (int m = 0; m < MODELS; m++) {
                vertices[m] = 0;
                BufferBuilder builder = new BufferBuilder(templateBytes, type.mode(), type.format());
                models[m].emit(poseStack, builder, type, 0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                try (MeshData mesh = builder.build()) {
                    if (mesh == null) continue;
                    if (format != null && mesh.drawState().format() != format) throw new IllegalStateException("aurora templates in two vertex formats");
                    format = mesh.drawState().format();
                    ByteBuffer bytes = mesh.vertexBuffer();
                    int size = bytes.remaining();
                    templateStage = ensure(templateStage, templateSize + size);
                    MemoryUtil.memCopy(MemoryUtil.memAddress(bytes), MemoryUtil.memAddress0(templateStage) + templateSize, size);
                    firstVertex[m] = templateVertices;
                    vertices[m] = mesh.drawState().vertexCount();
                    templateSize += size;
                    templateVertices += vertices[m];
                }
            }
        } finally {
            poseStack.popPose();
        }
        if (format == null) return;

        BufferUploader.invalidate();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, templateBuffer);
        GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, templateStage.clear().limit(templateSize), GL15.GL_STATIC_DRAW);
        templateFormat = format;
        templateRain = rainlevel;
        templateTurn.set(poseStack.last().pose()).setTranslation(0, 0, 0);
        templateNormal.set(poseStack.last().normal());
        for (int m = 0; m < MODELS; m++) {
            templatePasses[m] = models[m].passes();
        }
    }

    private static void feedback(int size, int drawn, int total, int shaderProgram, boolean check) {
        int query = check ? GL15.glGenQueries() : 0;
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._glUseProgram(program);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, tileTexture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, tileBuffer);
        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
        try {
            BufferUploader.invalidate();
            GlStateManager._glBindVertexArray(feedbackVao);
            if (check) GL15.glBeginQuery(GL30.GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN, query);
            long written = 0;
            int tileStart = 0;
            for (int m = 0; m < MODELS; m++) {
                if (counts[m] > 0 && vertices[m] > 0) {
                    long bytes = (long) counts[m] * vertices[m] * size;
                    GL20.glUniform1i(tileBaseUniform, tileStart);
                    GL30.glBindBufferRange(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, outputBuffer, written, bytes);
                    GL30.glBeginTransformFeedback(GL11.GL_POINTS);
                    GL31.glDrawArraysInstanced(GL11.GL_POINTS, firstVertex[m], vertices[m], counts[m]);
                    GL30.glEndTransformFeedback();
                    written += bytes;
                }
                tileStart += counts[m];
            }
            if (check) GL15.glEndQuery(GL30.GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN);
        } finally {
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
            GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
            GlStateManager._activeTexture(previousUnit);
            GlStateManager._glUseProgram(shaderProgram);
        }
        if (check) {
            int primitives = GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT);
            GL15.glDeleteQueries(query);
            if (primitives != drawn) {
                throw new IllegalStateException("transform feedback wrote " + primitives + " of " + drawn + " aurora vertices");
            }
        }
    }

    // the feedback shader copies each template vertex, moving it by the tile offset and giving it the tile's block light
    private static void buildProgram(int size, int lightWord) {
        if (program != 0) GL20.glDeleteProgram(program);
        if (feedbackVao != 0) GL30.glDeleteVertexArrays(feedbackVao);
        program = 0;
        feedbackVao = 0;
        int words = (size - 12) / 4;
        int groups = (words + 3) / 4;
        StringBuilder source = new StringBuilder("#version 150\nin vec3 Position;\nuniform samplerBuffer Tiles;\nuniform int TileBase;\nout vec3 outPosition;\n");
        String[] varyings = new String[groups + 1];
        varyings[0] = "outPosition";
        for (int g = 0; g < groups; g++) {
            String glslType = wordType(Math.min(4, words - g * 4));
            source.append("in ").append(glslType).append(" Word").append(g).append(";\n");
            source.append("flat out ").append(glslType).append(" outWord").append(g).append(";\n");
            varyings[g + 1] = "outWord" + g;
        }
        source.append("void main() {\n    vec4 tile = texelFetch(Tiles, TileBase + gl_InstanceID);\n    outPosition = Position + tile.xyz;\n");
        for (int g = 0; g < groups; g++) {
            source.append("    outWord").append(g).append(" = Word").append(g).append(";\n");
        }
        int r = lightWord - 3;
        String light = "outWord" + r / 4 + (Math.min(4, words - r / 4 * 4) == 1 ? "" : "." + "xyzw".charAt(r % 4));
        source.append("    ").append(light).append(" = (").append(light).append(" & 0xFFFF0000u) | max(").append(light)
                .append(" & 0xFFFFu, uint(tile.w));\n    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);\n}\n");

        int vertex = compile(GL20.GL_VERTEX_SHADER, source.toString());
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, "#version 150\nout vec4 fragColor;\nvoid main() {\n    fragColor = vec4(0.0);\n}\n");
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertex);
        GL20.glAttachShader(linked, fragment);
        GL20.glBindAttribLocation(linked, 0, "Position");
        for (int g = 0; g < groups; g++) {
            GL20.glBindAttribLocation(linked, 1 + g, "Word" + g);
        }
        GL30.glTransformFeedbackVaryings(linked, varyings, GL30.GL_INTERLEAVED_ATTRIBS);
        GL20.glLinkProgram(linked);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(linked);
            GL20.glDeleteProgram(linked);
            throw new IllegalStateException("aurora feedback program did not link: " + log);
        }
        program = linked;
        tileBaseUniform = GL20.glGetUniformLocation(linked, "TileBase");

        feedbackVao = GlStateManager._glGenVertexArrays();
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(feedbackVao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, templateBuffer);
        GlStateManager._enableVertexAttribArray(0);
        GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, size, 0L);
        for (int g = 0; g < groups; g++) {
            GlStateManager._enableVertexAttribArray(1 + g);
            GlStateManager._vertexAttribIPointer(1 + g, Math.min(4, words - g * 4), GL11.GL_UNSIGNED_INT, size, 12L + 16L * g);
        }
        VertexBuffer.unbind();
    }

    private static String wordType(int count) {
        return count == 1 ? "uint" : "uvec" + count;
    }

    private static int compile(int kind, String source) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("aurora feedback shader did not compile: " + log);
        }
        return shader;
    }

    private static ByteBuffer ensure(ByteBuffer buffer, int size) {
        if (buffer == null) return MemoryUtil.memAlloc(Math.max(size, 16384));
        if (buffer.capacity() >= size) return buffer;
        return MemoryUtil.memRealloc(buffer, Math.max(size, buffer.capacity() * 2));
    }
}
