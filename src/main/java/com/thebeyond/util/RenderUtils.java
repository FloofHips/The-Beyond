package com.thebeyond.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

public class RenderUtils {
    private static final RandomSource RANDOM = RandomSource.create();
    public static void renderModel(ResourceLocation loc, PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord) {
        renderModel(loc, poseStack, consumer, packedLight, overlayCoord, 1,1,1,1);
    }
    public static ModelPart bakeLayer(ModelLayerLocation location) {
        return Minecraft.getInstance().getEntityModels().bakeLayer(location);
    }

    public static void renderModel(ResourceLocation loc, PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord, int r, int g, int b, int a) {
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        ModelManager manager = Minecraft.getInstance().getModelManager();
        for (BakedModel pass : manager.getModel(ModelResourceLocation.standalone(loc)).getRenderPasses(ItemStack.EMPTY, true)) {
            consumer.setColor(r, g, b, 0);
            renderer.renderModelLists(pass, ItemStack.EMPTY, packedLight, overlayCoord, poseStack, consumer);
            consumer.setColor(255, 255, 255, 255);
        }
    }

    public static void renderAuroraModel(ResourceLocation modelLocation, PoseStack poseStack,
                                         MultiBufferSource buffer, int light, int overlay,
                                         float r, float g, float b, float a) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(ModelResourceLocation.standalone(modelLocation));

        if (model != mc.getModelManager().getMissingModel()) {
            VertexConsumer consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.translucent());
            List<BakedQuad> quads = model.getQuads(null, null, RANDOM, ModelData.EMPTY, null);

            for (BakedQuad quad : quads) {
                consumer.putBulkData(poseStack.last(), quad, r, g, b, a, light, overlay, true);
            }
        }
    }

    //public static void renderModel(ResourceLocation loc, PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlayCoord, int r, int g, int b, int a) {
    //    ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
    //    ModelManager manager = Minecraft.getInstance().getModelManager();
    //    for (BakedModel pass : manager.getModel(ModelResourceLocation.standalone(loc)).getRenderPasses(ItemStack.EMPTY, true)) {
    //        //for (RenderType type : pass.getRenderTypes(ItemStack.EMPTY, true)) {
    //        //VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffer, type, true, true);
    //        renderer.renderModelLists(pass, ItemStack.EMPTY, packedLight, overlayCoord, poseStack, consumer);
    //        //}
    //    }
    //}
}
