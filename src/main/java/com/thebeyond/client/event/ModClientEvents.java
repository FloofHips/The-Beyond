package com.thebeyond.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
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
import com.thebeyond.common.registry.*;
import com.thebeyond.util.ColorUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.awt.image.renderable.RenderContext;
import java.util.Collections;

@SuppressWarnings("unused")
@EventBusSubscriber(modid = TheBeyond.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    static RandomSource random = RandomSource.create(254572);
    public static PerlinSimplexNoise gellidVoidNoise = new PerlinSimplexNoise(random, Collections.singletonList(1));
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event){
        EntityRenderers.register(BeyondEntityTypes.ENDERGLOP.get(), EnderglopRenderer::new);

        ItemBlockRenderTypes.setRenderLayer(BeyondFluids.GELLID_VOID.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(BeyondFluids.GELLID_VOID_FLOWING.get(), RenderType.cutoutMipped());
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

        colors.register((state, reader, pos, tintIndex) -> {
            if (pos != null) {
                Vec3 B = new Vec3(202, 222, 234);
                Vec3 PR = new Vec3(168, 200, 207);
                Vec3 P = new Vec3(255, 227, 248);
                Vec3 G = new Vec3(202, 234, 221);
                Vec3 Y = new Vec3(239, 250, 218);

                return ColorUtils.getNoiseColor(pos, B, PR, P, G, Y);
            }
            return 0xFFFFFF;
        }, BeyondBlocks.PEARL.get(), BeyondBlocks.PEARL_BRICKS.get());
    }

    @SubscribeEvent
    public static void dimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event){
        event.register(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"), new EndSpecialEffects());
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getCamera().getEntity().level().dimensionType().effectsLocation().equals(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"))) {
            event.setCanceled(true);
            event.setFogShape(FogShape.SPHERE);
            event.setFarPlaneDistance((float) Minecraft.getInstance().cameraEntity.position().y + 30);
            event.setNearPlaneDistance(15);
       }
    }

    @SubscribeEvent
    public static void onSoundEvent(PlaySoundEvent event) {
        if(Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasEffect(BeyondEffects.DEAFENED)) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        //event.setRed(0);
        //event.setGreen(1);
        //event.setBlue(1);
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
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/gellid_void"),
                    STILL_2 = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/plate_block"),
                    FLOW = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/auroracite"),
                    OVERLAY = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/auroracite"),
                    VIEW_OVERLAY = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/block/auroracite.png");

            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }
//
            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOW;
            }
//
            @Override
            public ResourceLocation getOverlayTexture() {
                return OVERLAY;
            }
            @Override
            public ResourceLocation getStillTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                int offset = (getVoidWaveOffset(pos.getX(), pos.getY(), pos.getZ())) % 39;

                //System.out.println(offset);
                return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/gellid_void/gellid_void_" + Mth.abs(offset));
            }

            @Override
            public ResourceLocation getFlowingTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                int offset = (getVoidWaveOffset(pos.getX(), pos.getY(), pos.getZ())) % 39;
                //System.out.println(offset);
                return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/gellid_void/gellid_void_flowing_" + Mth.abs(offset));
            }

            public int getVoidWaveOffset(int x, int y, int z){
                double noiseX = x * 0.05;
                double noiseZ = z * 0.05;

                double noiseValue = gellidVoidNoise.getValue(noiseX, noiseZ, true);

                int noiseOffset = (int) (((noiseValue + 1.0) * 0.5) * 39);

                int totalOffset = (noiseOffset + Mth.abs(y)) % 40;

                return Mth.clamp(totalOffset, 0, 39);
            }

            @Override
            public ResourceLocation getRenderOverlayTexture(Minecraft mc) {
                return VIEW_OVERLAY;
            }


            @Override
            public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                int color = -5566465;
                return new Vector3f((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F);
            }

            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape) {
                nearDistance = -20F;
                farDistance = 2f;

                if (farDistance > renderDistance) {
                    farDistance = renderDistance;
                    shape = FogShape.SPHERE;
                }

                RenderSystem.setShaderFogStart(nearDistance);
                RenderSystem.setShaderFogEnd(farDistance);
                RenderSystem.setShaderFogShape(shape);
            }

            //@Override
            //public boolean renderFluid(FluidState fluidState, BlockAndTintGetter getter, BlockPos pos, VertexConsumer vertexConsumer, BlockState blockState) {
            //    new WaveFluidRenderer().tesselate(getter, pos, vertexConsumer, blockState, fluidState);
            //    return true;
            //}
        }, BeyondFluids.GELLID_VOID_TYPE.get());
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

        if (event.getCamera().getEntity().level().dimensionType().effectsLocation().equals(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"))) {
            if(!event.getCamera().getEntity().level().isRaining())
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
                if(chunkPos.x%2==0) break;

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
                    ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

                    ItemStack pickaxeStack = Items.RED_STAINED_GLASS_PANE.getDefaultInstance();

                    itemRenderer.renderStatic(
                            pickaxeStack,
                            ItemDisplayContext.GUI,
                            15728880,
                            OverlayTexture.NO_OVERLAY,
                            poseStack,
                            bufferSource, level, 1
                    );

                    poseStack.popPose();
                }
            }
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }
}
