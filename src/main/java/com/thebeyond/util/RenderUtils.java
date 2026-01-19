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
}
