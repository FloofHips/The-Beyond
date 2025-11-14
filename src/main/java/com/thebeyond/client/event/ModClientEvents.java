package com.thebeyond.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.specialeffects.EndSpecialEffects;
import com.thebeyond.client.model.*;

import com.thebeyond.client.particle.AuroraciteStepParticle;
import com.thebeyond.client.particle.GlopParticle;
import com.thebeyond.client.renderer.*;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.registry.*;
import com.thebeyond.util.ColorUtils;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
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
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import org.joml.Vector3f;

import java.nio.Buffer;
import java.util.Collections;

@SuppressWarnings("unused")
@EventBusSubscriber(modid = TheBeyond.MODID, value = Dist.CLIENT)
public class ModClientEvents {
    public static ShaderInstance ENTITY_DEPTH_SHADER;
    private static final ResourceLocation AURORA_TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/environment/aurora.png");
    public static final ResourceLocation CLOUD_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/cloud");
    public static final ResourceLocation CLOUD_2_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/cloud_2");
    public static final ResourceLocation AURORA_0_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_0");
    public static final ResourceLocation AURORA_1_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_1");
    public static final ResourceLocation AURORA_2_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_2");
    public static final ResourceLocation AURORA_3_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_3");
    public static final ResourceLocation AURORA_CRUMBLING_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/aurora_crumbling");
    static RandomSource random = RandomSource.create(254572);
    public static PerlinSimplexNoise gellidVoidNoise = new PerlinSimplexNoise(random, Collections.singletonList(1));
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event){
        EntityRenderers.register(BeyondEntityTypes.ENDERGLOP.get(), EnderglopRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.ENADRAKE.get(), EnadrakeRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.ENATIOUS_TOTEM.get(), EnatiousTotemRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.KNOCKBACK_SEED.get(), KnockBackSeedRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.POISON_SEED.get(), PoisonSeedRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.UNSTABLE_SEED.get(), UnstableSeedRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.LANTERN.get(), LanternRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.ABYSSAL_NOMAD.get(), AbyssalNomadRenderer::new);

        ItemBlockRenderTypes.setRenderLayer(BeyondFluids.GELLID_VOID.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(BeyondFluids.GELLID_VOID_FLOWING.get(), RenderType.cutoutMipped());
    }
    @SubscribeEvent
    public static void onAdditional(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(CLOUD_MODEL));
        event.register(ModelResourceLocation.standalone(CLOUD_2_MODEL));
        event.register(ModelResourceLocation.standalone(AURORA_0_MODEL));
        event.register(ModelResourceLocation.standalone(AURORA_1_MODEL));
        event.register(ModelResourceLocation.standalone(AURORA_2_MODEL));
        event.register(ModelResourceLocation.standalone(AURORA_3_MODEL));
        event.register(ModelResourceLocation.standalone(AURORA_CRUMBLING_MODEL));
    }
    @SubscribeEvent
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(BeyondModelLayers.ENDERDROP_LAYER, EnderdropModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.ENDERGLOP_LAYER, EnderglopModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.ENADRAKE, EnadrakeModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.ENATIOUS_TOTEM, EnatiousTotemModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.KNOCKBACK_SEED, KnockBackSeedModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.POISON_SEED, PoisonSeedModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.UNSTABLE_SEED, UnstableSeedModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.LANTERN_LEVIATHAN, LanternLeviathanModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.LANTERN_LARGE, LanternLargeModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.LANTERN_MEDIUM, LanternMediumModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.LANTERN_SMALL, LanternSmallModel::createBodyLayer);
        event.registerLayerDefinition(BeyondModelLayers.ABYSSAL_NOMAD, () -> AbyssalNomadModel.createBodyLayer(CubeDeformation.NONE));
        event.registerLayerDefinition(BeyondModelLayers.ABYSSAL_NOMAD_GLOW, () -> AbyssalNomadModel.createBodyLayer(new CubeDeformation(-0.1f)));
    }

    @SubscribeEvent
    public static void registerLayer(RegisterSpawnPlacementsEvent event){
        event.register(
                BeyondEntityTypes.LANTERN.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LanternEntity::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR
        );
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
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "rendertype_entity_depth"),
                    DefaultVertexFormat.NEW_ENTITY), BeyondShaders::setRenderTypeDepthOverlay);
        } catch (Exception exception) {
            TheBeyond.LOGGER.error("The Beyond could not register internal shaders! :(", exception);
        }
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

    //@SubscribeEvent
    //public static void onChunkLoadEvent(ChunkEvent.Load event) {
    //    event.getChunk().findBlocks();
    //}

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

    public static void renderClouds(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = player.level();

        //if (event.getCamera().getEntity().level().dimensionType().effectsLocation().equals(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"))) {
        //    if(!event.getCamera().getEntity().level().isRaining())
        //        return;
        //}

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();

        poseStack.pushPose();
        //poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        //int renderDistance = mc.options.getEffectiveRenderDistance();
        //ChunkPos playerChunk = player.chunkPosition();

        //int yLevel = 192;

        poseStack.pushPose();
        //poseStack.translate(0.5F - 0.5F * thickness, 1.9F, 0.5F);
        poseStack.scale(10, 10, 10);
        float time = level.getGameTime();

        poseStack.popPose();

        bufferSource.endBatch();
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void renderLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (!event.getCamera().getEntity().level().dimensionType().effectsLocation().equals(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"))) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = player.level();

        if (player == null || level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();


        float time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true);

        if (!(Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ())>300)) {
            renderClouds(poseStack, 122, 50, time/50f, CLOUD_MODEL, bufferSource);
            renderClouds(poseStack, 112, 90,time/80f, CLOUD_2_MODEL, bufferSource);
            renderClouds(poseStack, 102, 200,time/200f, CLOUD_2_MODEL, bufferSource);
        }

        //if (mc.level != null && mc.player != null && level instanceof ClientLevel clientLevel) {
        //    //AuroraBorealisRenderer.renderAurora(poseStack, 0, time, bufferSource, event, mc, player, level);
        //    AuroraBorealisRenderer.renderAurora(poseStack, event.getProjectionMatrix(), event.getPartialTick().getGameTimeDeltaPartialTick(false) , clientLevel, mc);
        //}

        renderAurora(poseStack, 0, time, bufferSource, event, mc, player, level);
        renderAurora(poseStack, 16, time, bufferSource, event, mc, player, level);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    public static void renderClouds(PoseStack poseStack, float translate, float scale, float time, ResourceLocation model, MultiBufferSource.BufferSource buffer) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotation(time));
        poseStack.translate(0, translate, 0);
        poseStack.scale(scale, scale/2f, scale);
        poseStack.mulPose(Axis.XP.rotation((float) -Math.PI/2f));
        poseStack.translate(-0.5, -0.5, -0.5);
        RenderUtils.renderModel(model, poseStack, buffer.getBuffer(RenderType.cutout()), 255, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    public static void renderAurora(PoseStack poseStack, float yoffset, float time, MultiBufferSource.BufferSource buffer, RenderLevelStageEvent event, Minecraft mc, Player player, Level level) {
        if (!event.getCamera().getEntity().level().isRaining()) return;

        Frustum frustum = event.getFrustum();
        int renderDistance = mc.options.getEffectiveRenderDistance();
        ChunkPos playerChunk = player.chunkPosition();
        int yLevel = 192;

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                if (chunkPos.z % 2 == 0) continue;
                if (chunkPos.x % 2 == 0) continue;
                if ((chunkPos.x % (4))+1 == 0) continue;
                if ((chunkPos.x % (4))-1 == 0) continue;
                double worldX = chunkPos.getMiddleBlockX();
                double worldZ = chunkPos.getMiddleBlockZ();
                double distanceFromCenter = Math.sqrt(worldX * worldX + worldZ * worldZ);
                if (distanceFromCenter < 100) continue;

                double distance = Math.sqrt(chunkPos.x * chunkPos.x + chunkPos.z * chunkPos.z);
                double maxDistance = renderDistance * Math.sqrt(2);
                float alpha = (float) (distance*2);

                BlockPos centerPos = new BlockPos(
                        chunkPos.getMiddleBlockX(),
                        yLevel,
                        chunkPos.getMiddleBlockZ()
                );

                AABB pickaxeAABB = new AABB(
                        centerPos.getX() - 128, centerPos.getY() - 128, centerPos.getZ() - 128,
                        centerPos.getX() + 128, centerPos.getY() + 128, centerPos.getZ() + 128
                );

                if (frustum.isVisible(pickaxeAABB)) {
                    poseStack.pushPose();

                    poseStack.translate(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                    float wiggle = Mth.sin((time + (chunkPos.z)*20)/10);
                    float wiggle2 = Mth.sin((time + yoffset + (chunkPos.z)*10)/20) + (Mth.sin(chunkPos.z/2f)*15);

                    poseStack.translate(wiggle2, wiggle, 0);
                    poseStack.translate(0, yoffset, 0);
                    poseStack.translate(-0.5f, -0.5f, -0.5f);
                    poseStack.scale( 32,32,32);
                    //poseStack.translate(4, 0, 4);

                    poseStack.mulPose(Axis.XP.rotationDegrees(-90f));

                    //poseStack.mulPose(Axis.XP.rotationDegrees(alpha));
                    poseStack.translate(0, -0.5f, -0.5f);

                    double chunkCenterX = chunkPos.getMiddleBlockX();
                    double chunkCenterZ = chunkPos.getMiddleBlockZ();
                    double deltaX = chunkCenterX - player.getX();
                    double deltaZ = chunkCenterZ - player.getZ();
                    double distanceFromPlayer = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    double maxPlayerDistance = renderDistance * 16 * 0.8;
                    float l = (float) (1.0 - (distanceFromPlayer / maxPlayerDistance));
                    float flash = (Mth.sin(((chunkPos.z+ yoffset)*10) + time/5f) + 1.0f) * 0.5f;
                    int color = (int) (255 * Math.max(l, flash));

                    //RenderUtils.renderModel(getAuroraModel(x, z, chunkPos, renderDistance), poseStack, buffer.getBuffer(BeyondRenderTypes.entityCutout(getAuroraTexture(x, z, chunkPos, renderDistance))), color, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                    RenderUtils.renderModel(getAuroraModel(x, z, chunkPos, renderDistance), poseStack, buffer.getBuffer(BeyondRenderTypes.cutout()), color, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);

                    poseStack.popPose();
                }
            }
        }
    }

    public static ResourceLocation getAuroraModel(int relX, int relZ, ChunkPos chunkPos, int renderDistance) {
        double worldX = chunkPos.getMiddleBlockX();
        double worldZ = chunkPos.getMiddleBlockZ();
        double distanceFromCenter = Math.sqrt(worldX * worldX + worldZ * worldZ);

        if (distanceFromCenter < renderDistance * 16 * 0.7) {
            return AURORA_CRUMBLING_MODEL;
        }

        if (Math.abs(relZ) == renderDistance-1 || Math.abs(relZ) == renderDistance+1 || Math.abs(relZ) == renderDistance) {
            return AURORA_CRUMBLING_MODEL;
        }

        int i = chunkPos.x + chunkPos.z;
        return switch (i % 3) {
            case 0 -> AURORA_0_MODEL;
            case 1 -> AURORA_1_MODEL;
            case 2 -> AURORA_2_MODEL;
            default -> AURORA_3_MODEL;
        };
    }

    public static ResourceLocation getAuroraTexture(int relX, int relZ, ChunkPos chunkPos, int renderDistance) {
        double worldX = chunkPos.getMiddleBlockX();
        double worldZ = chunkPos.getMiddleBlockZ();
        double distanceFromCenter = Math.sqrt(worldX * worldX + worldZ * worldZ);

        if (distanceFromCenter < renderDistance * 16 * 0.7) {
            return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_crumbling.png");
        }

        if (Math.abs(relZ) == renderDistance-1 || Math.abs(relZ) == renderDistance+1 || Math.abs(relZ) == renderDistance) {
            return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_crumbling.png");
        }

        int i = chunkPos.x + chunkPos.z;
        return switch (i % 3) {
            case 0 -> ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_0.png");
            case 1 -> ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_1.png");
            case 2 -> ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_2.png");
            default -> ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/models/aurora_3.png");
        };
    }
}
