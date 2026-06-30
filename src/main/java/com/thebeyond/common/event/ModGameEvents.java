package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.TotemOfRespiteEntity;
import com.thebeyond.common.item.AnchorLeggingsItem;
import com.thebeyond.common.awareness.*;
import com.thebeyond.common.network.GatedStructuresSyncPayload;
import com.thebeyond.common.network.PlayerAwarenessSyncPayload;
import com.thebeyond.common.registry.*;
import com.thebeyond.util.AOEManager;
import com.thebeyond.util.RefugeChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/** Gameplay event handlers that run on both sides - keep off the client-only bus so dedicated servers fire them too. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public class ModGameEvents {

    private static final String TAG_PENDING_TOTEM = "the_beyond:pendingTotem";
    private static final String TAG_TOTEM_ITEMS = "the_beyond:totemItems";
    private static final boolean CURIOS_LOADED = net.neoforged.fml.ModList.get().isLoaded("curios");

    private static RefugeChunkData getChunkData(ServerLevel level, BlockPos pos) {
        // Never force-load: a recursive load during DistanceManager.runAllUpdates throws CME.
        // An unloaded chunk has no refuge data anyway, so null means "no protection".
        ChunkAccess chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
        if (chunk != null) {
            RefugeChunkData data = chunk.getData(BeyondAttachments.REFUGE_DATA);
            if (data.shouldPreventHunger() || data.shouldPreventExplosion()
                    || data.shouldPreventMobSpawn() || data.shouldPreventFallDamage()) {
                return data;
            }
        }
        // For sub-levels the refuge data lives at the remote plot's storage offset.
        BlockPos stored = com.thebeyond.api.compat.BeyondCompatHooks.storedForVisible(level, pos);
        if (stored != null) {
            ChunkAccess sChunk = level.getChunkSource().getChunk(stored.getX() >> 4, stored.getZ() >> 4, false);
            if (sChunk != null) return sChunk.getData(BeyondAttachments.REFUGE_DATA);
        }
        return chunk == null ? null : chunk.getData(BeyondAttachments.REFUGE_DATA);
    }

    // NeoForge doesn't sync attachments to the client, so it starts empty on login, respawn, and
    // dimension change - we re-push the known set on all three or content gets re-hidden.
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) syncAwarenessTo(sp);
    }

    @SubscribeEvent
    public static void onPlayerRespawnSyncAwareness(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) syncAwarenessTo(sp);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimensionSyncAwareness(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) syncAwarenessTo(sp);
    }

    /** Sends the player's known set to their client - the shared world set in SHARED_WORLD mode, otherwise their own. */
    private static void syncAwarenessTo(ServerPlayer sp) {
        if (!BeyondAwareness.gateEnabled()) return;
        Set<ResourceLocation> snapshot;
        if (BeyondAwareness.mode() == AwarenessMode.SHARED_WORLD) {
            snapshot = Set.copyOf(WorldAwareness.get(sp.serverLevel()).all());
        } else {
            snapshot = Set.copyOf(sp.getData(BeyondAttachments.PLAYER_AWARENESS).all());
        }
        PacketDistributor.sendToPlayer(sp, new PlayerAwarenessSyncPayload(snapshot, true));
        PacketDistributor.sendToPlayer(sp, new GatedStructuresSyncPayload(HiddenContentFilter.gatedStructureMap(sp)));
    }

    /** Once a second, unlock whatever gated stuff the player is carrying or standing in. */
    @SubscribeEvent
    public static void onPlayerDiscoverNearby(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 20 != 0) return;
        HiddenContentFilter.discoverNearby(sp);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockPos pos = BlockPos.containing(event.getExplosion().center());
            RefugeChunkData data = getChunkData(serverLevel, pos);
            if (data != null && data.shouldPreventExplosion()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMobSpawn(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            RefugeChunkData data = getChunkData(serverLevel, event.getPos());
            if (data != null && data.shouldPreventMobSpawn()) {
                event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() != null && event.getEntity().level() instanceof ServerLevel serverLevel) {
            RefugeChunkData data = getChunkData(serverLevel, event.getEntity().getOnPos());
            if (data != null && data.shouldPreventFallDamage()) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onLand(LivingFallEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)
                || !livingEntity.getItemBySlot(EquipmentSlot.LEGS).is(BeyondItems.ANCHOR_LEGGINGS)) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer serverplayer
                && serverplayer.isShiftKeyDown()
                && serverplayer.level() instanceof ServerLevel serverlevel) {
            if (serverplayer.fallDistance > 1.5F && !serverplayer.isFallFlying()) {
                var enchantmentRegistry = serverlevel.registryAccess()
                        .registryOrThrow(Registries.ENCHANTMENT);
                Holder<Enchantment> powerHolder = enchantmentRegistry
                        .getHolderOrThrow(Enchantments.POWER);
                int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(
                        powerHolder, serverplayer.getItemBySlot(EquipmentSlot.LEGS));

                AnchorLeggingsItem.performSlam(serverplayer, serverplayer.fallDistance, powerLevel);
                event.setDamageMultiplier(powerLevel > 0 ? 0.3f / powerLevel : 0.3f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() != null && event.getEntity() instanceof LivingEntity livingEntity) {
            if (livingEntity.getItemBySlot(EquipmentSlot.HEAD).is(BeyondItems.ETHER_CLOAK.get())) {
                event.modifyVisibility(0.1f);
            }
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemStack item = event.getEntity().getItem();
        if (item.is(BeyondItems.LIVE_FLAME) || item.is(BeyondItems.LIVID_FLAME)) {
            boolean flag = item.is(BeyondItems.LIVE_FLAME);

            if (event.getPlayer().level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, event.getEntity().blockPosition(), BeyondSoundEvents.FLAME_FAIL.get(), SoundSource.NEUTRAL, 1, 0.8f + serverLevel.random.nextFloat());

                serverLevel.sendParticles(!flag ? BeyondParticleTypes.VOID_FLAME.get() : ParticleTypes.SOUL_FIRE_FLAME, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), 15, 0.5, 1, 0.5, 0.05);
                serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), 16, 0.02, 0.02, 0.02, 0.1);
            }

            event.getEntity().discard();
        }
    }

    @SubscribeEvent
    public static void onPlayer(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (player.tickCount % 20 != 0) return;
        if (player.level().dimension() != Level.END) return;
        if (!player.level().isThundering()) return;
        if (player.getY() < 192 || player.getY() > 208) return;

        BeyondCriteriaTriggers.MIGRATION_STORM.get().trigger(serverPlayer);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;

        if (event.getEntity() instanceof Player player) {
            if (!(player.getMainHandItem().is(BeyondItems.TOTEM_OF_RESPITE) || player.getOffhandItem().is(BeyondItems.TOTEM_OF_RESPITE)))
                return;

            if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))
                return;
            if (player.getHealth() > 0)
                return;
            CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persistent.remove(TAG_PENDING_TOTEM);
            persistent.remove(TAG_TOTEM_ITEMS);

            ListTag itemsList = new ListTag();
            RegistryAccess regAccess = player.registryAccess();

            boolean hasConsumedTotem = false;

            if (player.getMainHandItem().is(BeyondItems.TOTEM_OF_RESPITE)) {
                player.getMainHandItem().shrink(1);
                hasConsumedTotem = true;
            }
            if (!hasConsumedTotem && player.getOffhandItem().is(BeyondItems.TOTEM_OF_RESPITE)) {
                player.getOffhandItem().shrink(1);
            }

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

            // onDrop cancels the normal drops, so grab Curios into the totem here or they vanish.
            int curiosCount = 0;
            if (CURIOS_LOADED) {
                for (ItemStack stack : com.thebeyond.compat.curios.BeyondCuriosCompat.collectAndClear(player)) {
                    itemsList.add(stack.save(regAccess));
                    curiosCount++;
                }
            }

            if (!itemsList.isEmpty()) {
                persistent.put(TAG_TOTEM_ITEMS, itemsList);
                persistent.putBoolean(TAG_PENDING_TOTEM, true);
            }

            TheBeyond.LOGGER.info("[TotemRespite] totem captured items={} (curios among them={}) curiosLoaded={} corpse={}",
                    itemsList.size(), curiosCount, CURIOS_LOADED, net.neoforged.fml.ModList.get().isLoaded("corpse"));

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

            if (player instanceof ServerPlayer serverPlayer) {
                BeyondCriteriaTriggers.USE_TOTEM.get().trigger(serverPlayer);
            }

            persistent.remove(TAG_PENDING_TOTEM);
            persistent.remove(TAG_TOTEM_ITEMS);
        }
    }
}
