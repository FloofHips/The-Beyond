package com.thebeyond.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class RenderUtils {
    public static void renderModel(ResourceLocation loc, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlayCoord) {
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        ModelManager manager = Minecraft.getInstance().getModelManager();
        for (BakedModel pass : manager.getModel(ModelResourceLocation.standalone(loc)).getRenderPasses(ItemStack.EMPTY, true)) {
            for (RenderType type : pass.getRenderTypes(ItemStack.EMPTY, true)) {

                VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffer, type, true, true);
                renderer.renderModelLists(pass, ItemStack.EMPTY, packedLight, overlayCoord, poseStack, buffer.getBuffer(RenderType.cutout()));
            }
        }
    }
}
