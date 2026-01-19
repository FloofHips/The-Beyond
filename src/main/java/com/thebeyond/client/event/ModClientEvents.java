package com.thebeyond.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.specialeffects.EndSpecialEffects;
import com.thebeyond.client.gui.NomadsBlessingOverlay;
import com.thebeyond.client.model.*;

import com.thebeyond.client.model.equipment.ArmorModel;
import com.thebeyond.client.model.equipment.MultipartArmorModel;
import com.thebeyond.client.particle.AuroraciteStepParticle;
import com.thebeyond.client.particle.GlopParticle;
import com.thebeyond.client.renderer.*;
import com.thebeyond.client.renderer.blockentities.BonfireRenderer;
import com.thebeyond.client.renderer.blockentities.MemorFaucetRenderer;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.entity.TotemOfRespiteEntity;
import com.thebeyond.common.item.ModelArmorItem;
import com.thebeyond.common.registry.*;
import com.thebeyond.util.AOEManager;
import com.thebeyond.util.ColorUtils;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
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
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

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
        EntityRenderers.register(BeyondEntityTypes.RISING_BLOCK.get(), FallingBlockRenderer::new);

        ItemBlockRenderTypes.setRenderLayer(BeyondFluids.GELLID_VOID.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(BeyondFluids.GELLID_VOID_FLOWING.get(), RenderType.cutoutMipped());

        BlockEntityRenderers.register(BeyondBlockEntities.BONFIRE.get(), BonfireRenderer::new);
        BlockEntityRenderers.register(BeyondBlockEntities.MEMOR_FAUCET.get(), MemorFaucetRenderer::new);
    }
    @SubscribeEvent
    public static void onAdditional(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(CLOUD_MODEL));
        event.register(ModelResourceLocation.standalone(CLOUD_2_MODEL));
        event.register(ModelResourceLocation.standalone(AuroraBorealisRenderer.AURORA_0_MODEL));
        event.register(ModelResourceLocation.standalone(AuroraBorealisRenderer.AURORA_1_MODEL));
        event.register(ModelResourceLocation.standalone(AuroraBorealisRenderer.AURORA_2_MODEL));
        event.register(ModelResourceLocation.standalone(AuroraBorealisRenderer.AURORA_3_MODEL));
        event.register(ModelResourceLocation.standalone(AuroraBorealisRenderer.AURORA_CRUMBLING_MODEL));
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

    @SubscribeEvent
    public static void dimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event){
        event.register(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"), new EndSpecialEffects());
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getCamera().getEntity().level().dimensionType().effectsLocation().equals(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"))) {
            event.setCanceled(true);
            event.setFogShape(FogShape.SPHERE);

            float finalFog = finalEffectFog(event.getCamera());

            event.setFarPlaneDistance((float) (Minecraft.getInstance().cameraEntity.position().y + 30) * finalFog);
            event.setNearPlaneDistance(15 * finalFog);
       }
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

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() != null && event.getEntity() instanceof LivingEntity livingEntity) {
            if (livingEntity.getItemBySlot(EquipmentSlot.HEAD).is(BeyondItems.ETHER_CLOAK.get()))
            if (!event.canRender().isFalse())
                event.setCanRender(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() != null && event.getEntity() instanceof LivingEntity livingEntity) {
            if (livingEntity.getItemBySlot(EquipmentSlot.HEAD).is(BeyondItems.ETHER_CLOAK.get())) {
                event.modifyVisibility(0.1f);
            }
        }
    }

    @SubscribeEvent
    public static void onLand(LivingFallEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity && !livingEntity.getItemBySlot(EquipmentSlot.LEGS).is(BeyondItems.ANCHOR_LEGGINGS)) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer serverplayer && serverplayer.isShiftKeyDown() && serverplayer.level() instanceof ServerLevel serverlevel) {

            if (serverplayer.fallDistance > 1.5F && !serverplayer.isFallFlying()) {
                serverplayer.setSpawnExtraParticlesOnFall(true);
                SoundEvent soundevent = serverplayer.fallDistance > 5.0F ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
                serverlevel.playSound((Player)null, serverplayer.getX(), serverplayer.getY(), serverplayer.getZ(), soundevent, serverplayer.getSoundSource(), 1.0F, 1.0F);
                AOEManager.knockback(serverlevel, serverplayer, serverplayer);
                event.setDamageMultiplier(0.3f);
            }
        }
    }

    private static final String TAG_PENDING_TOTEM = "the_beyond:pendingTotem";
    private static final String TAG_TOTEM_ITEMS = "the_beyond:totemItems";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;

        if (event.getEntity() instanceof Player player) {
            if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))
                return;
            if (player.getHealth() > 0)
                return;
            CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persistent.remove(TAG_PENDING_TOTEM);
            persistent.remove(TAG_TOTEM_ITEMS);

            ListTag itemsList = new ListTag();
            RegistryAccess regAccess = player.registryAccess();

            for (ItemStack stack : player.getInventory().items) {
                if (!stack.isEmpty()) {
                    Tag itemTag = stack.save(regAccess);
                    itemsList.add(itemTag);
                }
            }

            for (ItemStack stack : player.getInventory().armor) {
                if (!stack.isEmpty()) {
                    Tag itemTag = stack.save(regAccess);
                    itemsList.add(itemTag);
                }
            }

            for (ItemStack stack : player.getInventory().offhand) {
                if (!stack.isEmpty()) {
                    Tag itemTag = stack.save(regAccess);
                    itemsList.add(itemTag);
                }
            }

            if (!itemsList.isEmpty()) {
                persistent.put(TAG_TOTEM_ITEMS, itemsList);
                persistent.putBoolean(TAG_PENDING_TOTEM, true);
            }

            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrop(LivingDropsEvent event) {
        if (event.isCanceled()) return;

        if (event.getEntity() instanceof Player player) {
            CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            if (persistent.getBoolean(TAG_PENDING_TOTEM)) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.level().isClientSide) return;

            CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

            if (!persistent.getBoolean(TAG_PENDING_TOTEM)) return;

            ListTag itemsList = persistent.getList(TAG_TOTEM_ITEMS, Tag.TAG_COMPOUND);
            if (itemsList.isEmpty()) return;

            TotemOfRespiteEntity totem = new TotemOfRespiteEntity(BeyondEntityTypes.TOTEM_OF_RESPITE.get(), player.level());
            totem.setPos(player.getX(), player.getY() + 1.5, player.getZ());
            totem.setOwner(player);

            RegistryAccess regAccess = player.registryAccess();

            for (Tag tag : itemsList) {
                if (tag instanceof CompoundTag itemTag) {
                    ItemStack stack = ItemStack.parse(regAccess, itemTag).orElse(ItemStack.EMPTY);

                    if (!stack.isEmpty()) {
                        totem.addItem(stack);
                    }
                }
            }

            player.level().addFreshEntity(totem);

            persistent.remove(TAG_PENDING_TOTEM);
            persistent.remove(TAG_TOTEM_ITEMS);
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
    public static void renderGui(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "nomad_eyes"), new NomadsBlessingOverlay());
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

            if (Minecraft.getInstance().gui.getBossOverlay().shouldCreateWorldFog()) {
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
