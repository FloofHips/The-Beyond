package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thebeyond.common.block.blockentities.MemorFaucetBlockEntity;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.vault.VaultClientData;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static com.thebeyond.common.block.MemorFaucetBlock.FACING;

public class MemorFaucetRenderer implements BlockEntityRenderer<MemorFaucetBlockEntity> {
    private final ItemRenderer itemRenderer;
    public MemorFaucetRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MemorFaucetBlockEntity bonBlockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float prog = bonBlockEntity.getActiveProgress();
        if (prog == 0) return;

        Level level = bonBlockEntity.getLevel();
        if (level == null) return;

        BlockPos pos = bonBlockEntity.getBlockPos();

        float time = (level.getGameTime() + partialTick) / 20.0f;
        Components.DynamicColorComponent colors = new Components.DynamicColorComponent(0.5f, 1.7f, 1.9f, 0.5f, 0, 0.2f, 0, 0.2f, 0xF000F0);

        for (int j = 0; j < 5; j++) {
            ItemStack fallbackStack = BeyondItems.REMEMBRANCE_LACE.toStack();
            ItemStack stack = bonBlockEntity.getItem(j);

            stack = stack.isEmpty() ? fallbackStack : stack;
            stack.set(BeyondComponents.COLOR_COMPONENT, colors);

            if (!stack.isEmpty()) {
                poseStack.pushPose();
                int x = bonBlockEntity.getBlockState().getValue(FACING).getStepX();
                int z = bonBlockEntity.getBlockState().getValue(FACING).getStepZ();
                poseStack.translate(-x*0.2, 0, -z*0.2);
                renderItem(poseStack, packedLight, packedOverlay, bufferSource, level, stack, j, time, pos, prog);
                poseStack.popPose();
            }
        }
    }

    private void renderItem(PoseStack poseStack, int packedLight, int packedOverlay, MultiBufferSource bufferSource, Level level, ItemStack itemstack, int index, float time, BlockPos pos, float progress) {
        poseStack.pushPose();
        Vec3 v = Vec3.atCenterOf(pos).subtract(Vec3.atLowerCornerOf(pos));
        poseStack.translate(v.x, 0.5, v.z);

        float radius = 0.8f * progress;
        float angle = time + (index * (2 * (float)Math.PI / 5));

        float x = (float)Math.cos(angle) * radius;
        float z = (float)Math.sin(angle) * radius;

        float bob = (float)Math.sin(time * 4 + index) * 0.1f;

        poseStack.translate(x, bob, z);

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        //poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        //poseStack.mulPose(Axis.YP.rotationDegrees(time * 20));

        poseStack.scale(0.5f, 0.5f, 0.5f);

        Minecraft.getInstance().getItemRenderer().renderStatic(itemstack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, level, (int) pos.asLong());

        poseStack.popPose();
    }

}