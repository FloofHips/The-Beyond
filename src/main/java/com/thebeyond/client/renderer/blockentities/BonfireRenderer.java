package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.common.block.BonfireBlock;
import com.thebeyond.common.block.blockentities.BonfireBlockEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

import static org.joml.Math.lerp;

public class BonfireRenderer implements BlockEntityRenderer<BonfireBlockEntity> {
    public BonfireRenderer(BlockEntityRendererProvider.Context context) {

    }

    @Override
    public AABB getRenderBoundingBox(BonfireBlockEntity blockEntity) {
        return BlockEntityRenderer.super.getRenderBoundingBox(blockEntity);
    }

    @Override
    public void render(BonfireBlockEntity bonBlockEntity, float v, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int i1) {
        if (bonBlockEntity.getBlockState().getValue(BonfireBlock.LIT)) {
            poseStack.translate(-0.1,0.5,-0.1);
            poseStack.scale(1.2f,1.5f,1.2f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.SOUL_FIRE.defaultBlockState(), poseStack, multiBufferSource, 255, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.CUTOUT);
        }
    }

    @Override
    public int getViewDistance() {
        return 500;
    }
}
