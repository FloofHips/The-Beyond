package com.thebeyond.util;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.client.event.ModClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

import java.awt.*;
import java.util.List;

public class RenderUtils {
    public static void renderModel(ResourceLocation loc, PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord) {
        Vector3f colors = new Vector3f(1,1,1);
        renderModel(loc, poseStack, consumer, packedLight, overlayCoord, colors.x, colors.y, colors.z,1);
    }

    public static ModelPart bakeLayer(ModelLayerLocation location) {
        return Minecraft.getInstance().getEntityModels().bakeLayer(location);
    }

    public static void renderModel(ResourceLocation loc, PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord, float r, float g, float b, float a) {
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        ModelManager manager = Minecraft.getInstance().getModelManager();
        for (BakedModel pass : manager.getModel(ModelResourceLocation.standalone(loc)).getRenderPasses(ItemStack.EMPTY, true)) {
            consumer.setColor(r, g, b, a);
            renderer.renderModelLists(pass, ItemStack.EMPTY, packedLight, overlayCoord, poseStack, consumer);
            consumer.setColor(1, 1, 1, 1);
        }
    }

    public static int lerpColor(float factor, int color1, int color2) {
        Color c1 = new Color(color1, true);
        Color c2 = new Color(color2, true);

        int r = (int)(c1.getRed() + (c2.getRed() - c1.getRed()) * factor);
        int g = (int)(c1.getGreen() + (c2.getGreen() - c1.getGreen()) * factor);
        int b = (int)(c1.getBlue() + (c2.getBlue() - c1.getBlue()) * factor);
        int a = (int)(c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void renderAdditiveQuad(GuiGraphics gui, ResourceLocation texture, int startx, int starty, int Uoffset, int Voffset, int width, int height, int texwidth, int texheight, int tintColor) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        gui.blit(texture, startx, starty, Uoffset, Voffset, width, height, texwidth, texheight);
        RenderSystem.defaultBlendFunc();
    }

    public static void renderMultiplicativeQuad(GuiGraphics gui, ResourceLocation texture, int startx, int starty, int Uoffset, int Voffset, int width, int height, int texwidth, int texheight, int tintColor) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.DST_COLOR,
                GlStateManager.DestFactor.ZERO,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
//
//        float a = ((tintColor >> 24) & 0xFF) / 255f;
//        float r = ((tintColor >> 16) & 0xFF) / 255f;
//        float g = ((tintColor >> 8) & 0xFF) / 255f;
//        float b = (tintColor & 0xFF) / 255f;
//
//        RenderSystem.setShaderColor(r, g, b, a);

        gui.blit(texture, startx, starty, Uoffset, Voffset, width, height, texwidth, texheight);

        //RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }

    /** A model resolved once per frame and reused across draws, skipping the per-call ModelManager
     *  lookup + getRenderPasses() allocation. Re-resolved each frame (no cross-frame cache). */
    public static final class ResolvedModel {
        private final List<BakedModel> passes;
        private ResolvedModel(List<BakedModel> passes) { this.passes = passes; }

        public static ResolvedModel resolve(ResourceLocation loc) {
            ModelManager manager = Minecraft.getInstance().getModelManager();
            return new ResolvedModel(manager.getModel(ModelResourceLocation.standalone(loc)).getRenderPasses(ItemStack.EMPTY, true));
        }

        public void emit(PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord, float r, float g, float b, float a) {
            ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
            for (BakedModel pass : passes) {
                consumer.setColor(r, g, b, a);
                renderer.renderModelLists(pass, ItemStack.EMPTY, packedLight, overlayCoord, poseStack, consumer);
                consumer.setColor(1, 1, 1, 1);
            }
        }
    }
}
