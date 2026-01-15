package com.thebeyond.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.client.event.ModClientEvents;
import net.minecraft.client.Minecraft;
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

import java.util.List;

public class RenderUtils {

    public static final int GHOSTLY_WHITE = 0x88FFFFFF;
    public static final int GHOSTLY_BLUE = 0x88CCEEFF;
    public static final int GHOSTLY_GREEN = 0x88CCFFCC;
    public static final int GHOSTLY_RED = 0x88FFCCCC;
    public static final int GHOSTLY_PURPLE = 0x88EECCFF;

    private static final RandomSource RANDOM = RandomSource.create();
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

    public static int getPulsingGhostlyColor(long time) {
        float pulse = (float) (Math.sin(time * 0.001) * 0.2 + 0.6);
        int alpha = (int) (pulse * 0x88);
        return (alpha << 24) | 0xCCEEFF;
    }

    public static boolean isGhostlyItem(ItemStack stack) {
        return true; //stack.getComponents().has() && stack.getTag().contains("isghostly") && stack.getTag().getBoolean("isghostly");
    }

    public static int getGhostlyColor(ItemStack stack, BakedQuad quad) {
        if (!isGhostlyItem(stack)) return -1;

        long time = System.currentTimeMillis();
        int baseColor = getPulsingGhostlyColor(time);

        int tintIndex = quad.getTintIndex();
        if (tintIndex == 1) {
            return (baseColor & 0xFF000000) | ((baseColor & 0x00FF00) << 8) | ((baseColor & 0x0000FF) >> 8);
        }

        return baseColor;
    }
}
