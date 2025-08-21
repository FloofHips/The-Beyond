package com.thebeyond.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.specialeffects.EndSpecialEffects;
import com.thebeyond.client.model.EnderdropModel;
import com.thebeyond.client.model.BeyondModelLayers;

import com.thebeyond.client.model.EnderglopModel;
import com.thebeyond.client.particle.AuroraciteStepParticle;
import com.thebeyond.client.particle.GlopParticle;
import com.thebeyond.client.renderer.EnderglopRenderer;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.util.ColorUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@SuppressWarnings("unused")
@EventBusSubscriber(modid = TheBeyond.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event){
        EntityRenderers.register(BeyondEntityTypes.ENDERGLOP.get(), EnderglopRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(BeyondModelLayers.ENDERDROP_LAYER, EnderdropModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.ENDERGLOP_LAYER, EnderglopModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BeyondParticleTypes.GLOP.get(), sprites
                -> (simpleParticleType, clientLevel, d, e, f, g, h, i)
                -> new GlopParticle(clientLevel, d, e, f, sprites));
        event.registerSpriteSet(BeyondParticleTypes.AURORACITE_STEP.get(), sprites
                -> (simpleParticleType, clientLevel, d, e, f, g, h, i)
                -> new AuroraciteStepParticle(clientLevel, d, e, f, sprites));
    }

    @SubscribeEvent
    public static void colorSetupBlock(RegisterColorHandlersEvent.Block event) {
        BlockColors colors = event.getBlockColors();
        colors.register((state, reader, pos, tintIndex) -> {
            if (pos != null) {
                Vec3 Blue_0 = new Vec3(30, 147, 155);
                Vec3 Blue_1 = new Vec3(32, 123, 187);
                Vec3 Blue_2 = new Vec3(29, 94, 173);
                Vec3 Blue_3 = new Vec3(22, 53, 84);
                Vec3 Blue_4 = new Vec3(29, 94, 173);

                return ColorUtils.getNoiseColor(pos, Blue_0, Blue_1, Blue_2, Blue_3, Blue_3);
            }
            return 0xFFFFFF;
        }, BeyondBlocks.AURORACITE.get());
    }

    @SubscribeEvent
    public static void dimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event){
        event.register(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"), new EndSpecialEffects());
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event){
        event.setCanceled(true);
        event.setFogShape(FogShape.SPHERE);
        event.setFarPlaneDistance((float) Minecraft.getInstance().cameraEntity.position().y + 30);
        event.setNearPlaneDistance(15);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        //Minecraft minecraft = Minecraft.getInstance();
        //ClientLevel level = minecraft.level;
        //if (level == null) return;
//
        //Camera camera = minecraft.gameRenderer.getMainCamera();
        //BlockPos cameraPos = BlockPos.containing(camera.getPosition());
        //Biome biome = level.getBiome(cameraPos).value();
        //float gamma = Minecraft.getInstance().options.gamma().get().floatValue();
//
        //int fogColor = biome.getFogColor();
        //float r = ((fogColor >> 16) & 0xFF) / 255.0f;
        //float g = ((fogColor >> 8) & 0xFF) / 255.0f;
        //float b = (fogColor & 0xFF) / 255.0f;
//
        //Vec3 rgbfogColor = new Vec3(r, g, b);
        //Vec3 skyColorVector = level.effects().getBrightnessDependentFogColor(rgbfogColor, 0);
//
        //event.setRed((float) skyColorVector.x * gamma);
        //event.setGreen((float) skyColorVector.y * gamma);
        //event.setBlue((float) skyColorVector.z * gamma);
        //event.setRed(event.getRed() / gamma);
        //event.setGreen(event.getGreen() / gamma);
        //event.setBlue(event.getBlue() / gamma);
    }

    @SubscribeEvent
    public static void renderLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = player.level();

        if (player == null || level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        int renderDistance = mc.options.getEffectiveRenderDistance();
        ChunkPos playerChunk = player.chunkPosition();

        int yLevel = 192;

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                //if(chunkPos.x%2==0) break;

                BlockPos centerPos = new BlockPos(
                        chunkPos.getMiddleBlockX(),
                        yLevel,
                        chunkPos.getMiddleBlockZ()
                );

                AABB pickaxeAABB = new AABB(
                        centerPos.getX() - 16, centerPos.getY(),
                        centerPos.getZ() - 16,
                        centerPos.getX() + 16, centerPos.getY() + 2,
                        centerPos.getZ() + 16
                );

                if (frustum.isVisible(pickaxeAABB)) {
                    poseStack.pushPose();

                    poseStack.translate(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                    float time = level.getGameTime();
                    float wiggle = Mth.sin((time + (chunkPos.z)*20)/10) / 2;
                    float wiggle2 = Mth.sin((time + (chunkPos.z)*10)/20);

                    poseStack.translate(wiggle2, wiggle, 0);
                    poseStack.scale(16, 16, 16);

                    poseStack.mulPose(Axis.XP.rotationDegrees(-90f));

                    poseStack.translate(0, -0.5f, -0.5f);
                    BlockRenderDispatcher itemRenderer = Minecraft.getInstance().getBlockRenderer();

                    BlockState pickaxeStack = Blocks.CHAIN.defaultBlockState();

                    itemRenderer.renderSingleBlock(
                            pickaxeStack, poseStack,
                            bufferSource,
                            15728880,
                            OverlayTexture.NO_OVERLAY
                    );

                    poseStack.popPose();
                }
            }
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }
}
