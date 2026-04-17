package com.thebeyond.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.thebeyond.BeyondConfig;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.specialeffects.EndSpecialEffects;
import com.thebeyond.client.gui.NomadsBlessingOverlay;
import com.thebeyond.client.gui.RefugeScreen;
import com.thebeyond.client.model.*;

import com.thebeyond.client.model.equipment.ArmorModel;
import com.thebeyond.client.model.equipment.MultipartArmorModel;
import com.thebeyond.client.particle.AuroraciteStepParticle;
import com.thebeyond.client.particle.GlopParticle;
import com.thebeyond.client.particle.PixelParticle;
import com.thebeyond.client.particle.SmokeParticle;
import com.thebeyond.client.renderer.*;
import com.thebeyond.client.renderer.blockentities.BonfireRenderer;
import com.thebeyond.client.renderer.blockentities.MemorFaucetRenderer;
import com.thebeyond.client.renderer.blockentities.RefugeRenderer;
import com.thebeyond.common.block.RefugeBlock;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.entity.TotemOfRespiteEntity;
import com.thebeyond.common.item.LiveFlameItem;
import com.thebeyond.common.item.ModelArmorItem;
import com.thebeyond.common.registry.*;
import com.thebeyond.mixin.AbstractSoundInstanceAccessor;
import com.thebeyond.util.AOEManager;
import com.thebeyond.util.ColorUtils;
import com.thebeyond.util.RefugeChunkData;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.*;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@EventBusSubscriber(modid = TheBeyond.MODID, value = Dist.CLIENT)
public class ModClientEvents {
    protected static Collection<ModelArmorItem> MODEL_ARMOR = new ArrayList<>();
    private static final ResourceLocation AURORA_TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/environment/aurora.png");
    public static final ResourceLocation CLOUD_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/cloud");
    public static final ResourceLocation CLOUD_2_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/cloud_2");

    public static final ResourceLocation ROOT_MODEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/root_shockwave");

    public static final ResourceLocation ROOT_BOOT = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/root_boot");
    public static final ResourceLocation ROOT_FOOD = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/root_food");
    public static final ResourceLocation ROOT_SHIELD = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/root_shield");
    public static final ResourceLocation ROOT_SWORD = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "models/root_sword");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/red_concrete.png");

    static RandomSource random = RandomSource.create(254572);
    public static PerlinSimplexNoise gellidVoidNoise = new PerlinSimplexNoise(random, Collections.singletonList(1));
    public static float bossFog = 0;
    public static float effectFog = 1;
    public static float nomadEyes = 0;
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
        EntityRenderers.register(BeyondEntityTypes.TOTEM_OF_RESPITE.get(), TotemOfRespiteRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.GRAVISTAR.get(), ThrownItemRenderer::new);
        EntityRenderers.register(BeyondEntityTypes.RISING_BLOCK.get(), FallingBlockRenderer::new);

        ItemBlockRenderTypes.setRenderLayer(BeyondFluids.GELLID_VOID.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(BeyondFluids.GELLID_VOID_FLOWING.get(), RenderType.cutoutMipped());

        BlockEntityRenderers.register(BeyondBlockEntities.BONFIRE.get(), BonfireRenderer::new);
        BlockEntityRenderers.register(BeyondBlockEntities.MEMOR_FAUCET.get(), MemorFaucetRenderer::new);
        BlockEntityRenderers.register(BeyondBlockEntities.REFUGE.get(), RefugeRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BeyondMenus.REFUGE.get(), RefugeScreen::new);
    }

    @SubscribeEvent
    public static void onAdditional(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation resourceLocation : Arrays.asList(CLOUD_MODEL, CLOUD_2_MODEL, ROOT_MODEL, ROOT_BOOT, ROOT_FOOD, ROOT_SHIELD, ROOT_SWORD, AuroraBorealisRenderer.AURORA_0_MODEL, AuroraBorealisRenderer.AURORA_1_MODEL, AuroraBorealisRenderer.AURORA_2_MODEL, AuroraBorealisRenderer.AURORA_3_MODEL, AuroraBorealisRenderer.AURORA_CRUMBLING_MODEL)) {
            event.register(ModelResourceLocation.standalone(resourceLocation));
        }
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


        BeyondItems.ITEMS.getEntries().stream()
                .filter(item -> item.get() instanceof ModelArmorItem)
                .map(item -> (ModelArmorItem) item.get())
                .forEach(armor -> {

                    EquipmentSlot slot = armor.getEquipmentSlot();
                    MultipartArmorModel model = armor.getArmorModel();
                    event.registerLayerDefinition(model.getLayerLocation(slot), ArmorModel.wrap(model.getLayerDefinition(slot), model.textureWidth(slot), model.textureHeight(slot)));

                });
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event){
        event.register(
                BeyondEntityTypes.LANTERN.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LanternEntity::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR
        );

        event.register(
                BeyondEntityTypes.ABYSSAL_NOMAD.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbyssalNomadEntity::checkMonsterSpawnRules,
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

        event.registerSpriteSet(BeyondParticleTypes.VOID_FLAME.get(), sprites
                -> new FlameParticle.Provider(sprites));

        event.registerSpriteSet(BeyondParticleTypes.SMOKE.get(),
                sprites -> new SmokeParticle.Provider(sprites));

        event.registerSpriteSet(BeyondParticleTypes.PIXEL.get(),
                sprites -> new PixelParticle.Provider(sprites));
    }
    public static void addModelArmor(ModelArmorItem item) {
        MODEL_ARMOR.add(item);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "rendertype_entity_depth"),
                    DefaultVertexFormat.NEW_ENTITY), BeyondShaders::setRenderTypeDepthOverlay);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "rendertype_refuge_gradient"),
                    DefaultVertexFormat.NEW_ENTITY), BeyondShaders::setRefugeGradient);
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

        //colors.register((state, reader, pos, tintIndex) -> {
        //    if (pos != null) {
        //        Vec3 B = new Vec3(202, 222, 234);
        //        Vec3 PR = new Vec3(168, 200, 207);
        //        Vec3 P = new Vec3(255, 227, 248);
        //        Vec3 G = new Vec3(202, 234, 221);
        //        Vec3 Y = new Vec3(239, 250, 218);
//
        //        return ColorUtils.getNoiseColor(pos, B, PR, P, G, Y);
        //    }
        //    return 0xFFFFFF;
        //}, BeyondBlocks.PEARL.get(), BeyondBlocks.PEARL_BRICKS.get());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void dimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event){
        EndSpecialEffects effects = new EndSpecialEffects();
        event.register(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"), effects);
        // Also register for the vanilla key. When another mod (e.g. BetterEnd) overrides
        // the End dimension type's effectsLocation back to minecraft:the_end, Beyond's custom
        // sky, fog color, and lightmap adjustments must still apply. Priority LOW ensures this
        // handler runs after other mods' NORMAL-priority handlers, so Beyond's registration
        // takes precedence (last writer wins in the DimensionSpecialEffects map).
        event.register(ResourceLocation.withDefaultNamespace("the_end"), effects);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Entity cameraEntity = event.getCamera().getEntity();
        if (cameraEntity == null) return;
        // Gated by enableCustomFog clientside config — when disabled, the vanilla
        // fog for the End runs unchanged. Mirrors FogRendererMixin's gate.
        if (cameraEntity.level().dimension() == Level.END
                && BeyondConfig.ENABLE_CUSTOM_FOG.get()) {
            event.setCanceled(true);
            event.setFogShape(FogShape.SPHERE);

            float finalFog = finalEffectFog(event.getCamera());

            float y = (float) cameraEntity.position().y;
            // Clamp minimum fog end to 30 blocks so fog stays valid at negative Y
            // (Enderscape extends End min height to y=-64)
            float fogEnd = Math.max((y + 30) * finalFog, 30 * finalFog);
            event.setFarPlaneDistance(fogEnd);
            event.setNearPlaneDistance(15 * finalFog);
       }
    }

    @SubscribeEvent
    public static void onColorFog(ViewportEvent.ComputeFogColor event) {

    }

    public static float finalEffectFog(Camera camera) {
        int type = doesMobEffectBlockSky(camera);
        if (type == 0) {
            effectFog = (float) Mth.lerp(0.05, effectFog, 1);
            return (float) Math.clamp(effectFog, 0.05, 1);
        } else if (type == 1) {
            effectFog = (float) Mth.lerp(0.05, effectFog, 0);
            return (float) Math.clamp(effectFog, 0.05, 1);
        }
        effectFog = 1;
        return 1;
    }

    private static int doesMobEffectBlockSky(Camera camera) {
        if (camera.getEntity() instanceof LivingEntity livingentity) {
            if (livingentity.hasEffect(MobEffects.BLINDNESS)) return 1;
            if (livingentity.hasEffect(MobEffects.DARKNESS)) return 2;
        }
        return 0;
    }

    @SubscribeEvent
    public static void onSoundEvent(PlaySoundEvent event) {
        if(Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasEffect(BeyondEffects.DEAFENED)) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
            event.setSound(null);
        }
    }

//    @SubscribeEvent
//    public static void onSoundEvent(PlayLevelSoundEvent.AtPosition event) {
//        if (event.getOriginalVolume() < 0.1f) return;
//        if (skipSound(event.getSound().value())) return;
//        scheduleDelayedSound(event, 2, event.getOriginalVolume()*0.5f);
//        //scheduleDelayedSound(event, 5, event.getOriginalVolume()*0.25f);
//    }
//
//    private static void scheduleDelayedSound(PlayLevelSoundEvent.AtPosition event, int delayTicks, float volume) {
//        SimpleSoundInstance sound = new SimpleSoundInstance(
//                event.getSound().value().getLocation(),
//                SoundSource.AMBIENT,
//                volume,
//                1.0f,
//                SoundInstance.createUnseededRandom(),
//                false,
//                0,
//                SoundInstance.Attenuation.LINEAR,
//                event.getPosition().x,
//                event.getPosition().y,
//                event.getPosition().z,
//                false
//        );
//
//        Minecraft.getInstance().getSoundManager().playDelayed(sound, delayTicks);
//    }
//
//    private static boolean skipSound(SoundEvent sound) {
//        if (sound == null) return true;
//        String str = sound.getLocation().toString();
//
//        if (str.contains("ambient") || str.contains("music") || str.contains("weather")) {
//            return true;
//        }
//
//        if (str.contains("entity") || str.contains("block") || str.contains("item") || str.contains("ui")) {
//            return false;
//        }
//        return false;
//    }

//    private static Optional<BlockPos> getRefuge(ServerLevel serverLevel, BlockPos pos) {
//        return serverLevel.getPoiManager().findClosest(
//                poiType -> poiType.is(BeyondPoiTypes.REFUGE),
//                blockPos -> {
//                    BlockState state = serverLevel.getBlockState(blockPos);
//                    return state.is(BeyondBlocks.REFUGE.get()) && RefugeBlock.isActive(state);},
//                pos,
//                80,
//                PoiManager.Occupancy.ANY
//        );
//    }

    // Server-side Refuge, Totem, and gameplay handlers moved to ModGameEvents.java
    // so they register on dedicated servers (not just Dist.CLIENT).

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() != null && event.getEntity() instanceof LivingEntity livingEntity) {
            if (livingEntity.getItemBySlot(EquipmentSlot.HEAD).is(BeyondItems.ETHER_CLOAK.get()))
            if (!event.canRender().isFalse())
                event.setCanRender(TriState.FALSE);
        }
    }


    //@SubscribeEvent
    //public static void onChunkLoadEvent(ChunkEvent.Unload event) {
    //    event.getChunk().getbloc
    //}
    //@SubscribeEvent
    //public static void onChunkLoadEvent(ChunkEvent.Load event) {
        //event.getChunk().findBlocks();
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
        BeyondItems.ITEMS.getEntries().stream()
                .filter(item -> item.get() instanceof ModelArmorItem)
                .map(item -> (ModelArmorItem) item.get())
                .forEach(armor -> {
                    event.registerItem(new IClientItemExtensions() {
                        @Override
                        @NotNull
                        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                            ArmorModel part = armor.getArmorModel().getModelPart(slot);
                            part.setup(entity, stack, slot, original);
                            return part;
                        }

                        @Override
                        public HumanoidModel<?> getGenericArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                            return getHumanoidArmorModel(entity, stack, slot, original);
                        }
                    }, armor);
                });
        //MODEL_ARMOR.clear();

        event.registerFluidType(new IClientFluidTypeExtensions() {
            private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/gellid_void/gellid_void_0"),
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
                // Jade and other overlay mods may call this without a valid BlockPos (null)
                // when rendering fluid icons in tooltips. Return the static base texture as
                // fallback instead of NPE-ing on pos.getX(). (Reda's fix)
                if (pos == null) return STILL;
                int offset = (getVoidWaveOffset(pos.getX(), pos.getY(), pos.getZ())) % 39;
                return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/gellid_void/gellid_void_" + Mth.abs(offset));
            }

            @Override
            public ResourceLocation getFlowingTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                // Same null-safety as getStillTexture — overlay mods may omit BlockPos.
                if (pos == null) return FLOW;
                int offset = (getVoidWaveOffset(pos.getX(), pos.getY(), pos.getZ())) % 39;
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
        }, BeyondFluids.GELLID_VOID_TYPE.get());
    }

    @SubscribeEvent
    public static void renderGui(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "nomad_eyes"), new NomadsBlessingOverlay());
    }

    @SubscribeEvent
    public static void renderLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        //renderRefugeDebug(event);

        Entity camEntity = event.getCamera().getEntity();
        if (camEntity == null || camEntity.level().dimension() != Level.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        Level level = player.level();

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();


        float time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true);

        if (!(Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ())>300)) {

            // Detect any active boss fight. shouldCreateWorldFog() only returns true for
            // Java ServerBossEvent boss bars (vanilla dragon). Stellarity uses command boss
            // bars (/bossbar add) which never set that flag. Fall back to checking if the
            // BossHealthOverlay has any entries at all — this catches both vanilla and
            // Stellarity boss bars so the 3D cloud rings render during any End boss fight.
            boolean bossActive = Minecraft.getInstance().gui.getBossOverlay().shouldCreateWorldFog();
            if (!bossActive) {
                var overlay = Minecraft.getInstance().gui.getBossOverlay();
                if (overlay instanceof com.thebeyond.mixin.client.BossHealthOverlayAccessor accessor) {
                    bossActive = !accessor.the_beyond$getEvents().isEmpty();
                }
            }
            if (bossActive) {
                bossFog = Mth.lerp(0.05f , bossFog, 1);
            } else {
                bossFog = Mth.lerp(0.05f , bossFog, 0);
            }
            if (bossFog != 0) {
                renderClouds(poseStack, 132, 50, time/50f, CLOUD_MODEL, bufferSource);
                renderClouds(poseStack, 122, 90,time/80f, CLOUD_2_MODEL, bufferSource);
                renderClouds(poseStack, 112, 200,time/200f, CLOUD_2_MODEL, bufferSource);
            }
        }
        AuroraBorealisRenderer.renderAurora(poseStack, 0, time, bufferSource, event, mc, player, level);
        AuroraBorealisRenderer.renderAurora(poseStack, 16, time, bufferSource, event, mc, player, level);

        bufferSource.endBatch();
        poseStack.popPose();
    }

//    private static void renderRefugeDebug(RenderLevelStageEvent event) {
//        Minecraft mc = Minecraft.getInstance();
//        Player player = mc.player;
//        Level level = player.level();
//        Frustum frustum = event.getFrustum();
//        int renderDistance = mc.options.getEffectiveRenderDistance();
//        ChunkPos playerChunk = player.chunkPosition();
//        int yLevel = (int) (-50);
//
//        for (int x = -renderDistance; x <= renderDistance; x++) {
//            for (int z = -renderDistance; z <= renderDistance; z++) {
//                ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
//
//                double worldX = chunkPos.getMiddleBlockX();
//                double worldZ = chunkPos.getMiddleBlockZ();
//
//                BlockPos centerPos = new BlockPos(
//                        chunkPos.getMiddleBlockX(),
//                        yLevel,
//                        chunkPos.getMiddleBlockZ()
//                );
//
//                RefugeChunkData data = level.getChunkAt(centerPos).getData(BeyondAttachments.REFUGE_DATA);
//
//                PoseStack poseStack = event.getPoseStack();
//                Vec3 camPos = event.getCamera().getPosition();
//
//                poseStack.pushPose();
//                poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
//
//                poseStack.translate(centerPos.getX(), centerPos.getY(), centerPos.getZ());
//
//                //poseStack.translate(0, yoffset, 0);
//                poseStack.translate(-0.5f, -0.5f, -0.5f);
//
//                poseStack.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
//
//                poseStack.translate(0, -0.5f, -0.5f);
//
//                double chunkCenterX = chunkPos.getMiddleBlockX();
//                double chunkCenterZ = chunkPos.getMiddleBlockZ();
//                double deltaX = chunkCenterX - player.getX();
//                double deltaZ = chunkCenterZ - player.getZ();
//                double distanceFromPlayer = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
//                double maxPlayerDistance = renderDistance * 16 * 0.8;
//                float l = (float) (1.0 - (distanceFromPlayer / maxPlayerDistance));
//
//                MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
//
//                if (data.shouldPreventExplosion()) {
//                    ItemStack itemStack = new ItemStack(Blocks.TNT.asItem());
//                    Minecraft.getInstance().getItemRenderer().renderStatic(itemStack, ItemDisplayContext.FIXED, 255, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, (int) l);
//                } else {
//                    ItemStack itemStack = new ItemStack(Blocks.BARRIER.asItem());
//                    Minecraft.getInstance().getItemRenderer().renderStatic(itemStack, ItemDisplayContext.FIXED, 255, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, (int) l);
//                }
//                if (data.shouldPreventHunger()) {
//                    poseStack.translate(0,1,0);
//                    ItemStack itemStack = new ItemStack(Items.COOKED_BEEF);
//                    Minecraft.getInstance().getItemRenderer().renderStatic(itemStack, ItemDisplayContext.FIXED, 255, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, (int) l);
//                } else {
//                    poseStack.translate(0,1,0);
//                    ItemStack itemStack = new ItemStack(Blocks.BARRIER.asItem());
//                    Minecraft.getInstance().getItemRenderer().renderStatic(itemStack, ItemDisplayContext.FIXED, 255, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, (int) l);
//                }
//                if (data.shouldPreventFallDamage()) {
//                    poseStack.translate(0,1,0);
//                    ItemStack itemStack = new ItemStack(Items.DIAMOND_BOOTS);
//                    Minecraft.getInstance().getItemRenderer().renderStatic(itemStack, ItemDisplayContext.FIXED, 255, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, (int) l);
//                } else {
//                    poseStack.translate(0,1,0);
//                    ItemStack itemStack = new ItemStack(Blocks.BARRIER.asItem());
//                    Minecraft.getInstance().getItemRenderer().renderStatic(itemStack, ItemDisplayContext.FIXED, 255, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, (int) l);
//                }
//                if (data.shouldPreventMobSpawn()) {
//                    poseStack.translate(0,1,0);
//                    ItemStack itemStack = new ItemStack(Blocks.SKELETON_SKULL.asItem());
//                    Minecraft.getInstance().getItemRenderer().renderStatic(itemStack, ItemDisplayContext.FIXED, 255, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, (int) l);
//                } else {
//                    poseStack.translate(0,1,0);
//                    ItemStack itemStack = new ItemStack(Blocks.BARRIER.asItem());
//                    Minecraft.getInstance().getItemRenderer().renderStatic(itemStack, ItemDisplayContext.FIXED, 255, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, (int) l);
//                }
//                poseStack.popPose();
//
//            }
//        }
//    }

    public static void renderClouds(PoseStack poseStack, float translate, float scale, float time, ResourceLocation model, MultiBufferSource.BufferSource buffer) {
        poseStack.pushPose();
        boolean flag = scale == 50;

        float trueScale = flag ? scale : scale * bossFog;

        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotation(time));
        poseStack.translate(0, translate, 0);
        poseStack.translate(0, (1 - bossFog) * (flag ? 20 : 50), 0);
        poseStack.scale(trueScale, trueScale / 2f, trueScale);

        poseStack.mulPose(Axis.XP.rotation((float) -Math.PI/2f));
        poseStack.translate(-0.5, -0.5, -0.5);


        RenderUtils.renderModel(model, poseStack, buffer.getBuffer(RenderType.cutout()), 255, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }


}
