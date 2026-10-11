package com.thebeyond.common.item;

import com.thebeyond.client.model.equipment.MultipartArmorModel;
import com.thebeyond.common.registry.BeyondSoundEvents;
import com.thebeyond.util.AOEManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class AnchorLeggingsItem extends ModelArmorItem {
    /** Fall distance for creative players, integrated from velocity since their fall damage skips LivingFallEvent. */
    private static final Map<UUID, Float> creativeFallDistance = new HashMap<>();

    public AnchorLeggingsItem(Holder<ArmorMaterial> material, Type type, Properties properties, Supplier<MultipartArmorModel> modelSupplier) {
        super(material, type, properties, modelSupplier);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return super.supportsEnchantment(stack, enchantment) || enchantment.is(Enchantments.POWER);
    }
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (slotId != 37) {
            super.inventoryTick(stack, level, entity, slotId, isSelected);
            return;
        }
        if (entity instanceof Player player) {
            Registry<Enchantment> enchantmentRegistry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> powerHolder = enchantmentRegistry.getHolderOrThrow(Enchantments.POWER);
            int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(powerHolder, stack);

            // push down only while airborne, on the ground it micro-falls every tick and loops the landing sound
            if (player.isShiftKeyDown() && !player.onGround()) {
                player.stopFallFlying();
                player.setDeltaMovement(player.getDeltaMovement().subtract(0, 0.08 * (1 + 0.5 * powerLevel), 0));
                player.hurtMarked = true;
            }

            // creative slam: with mayfly LivingFallEvent never fires, so the fall is tracked from velocity
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                    && player.getAbilities().mayfly) {
                UUID uuid = player.getUUID();
                float tracked = creativeFallDistance.getOrDefault(uuid, 0f);

                if (!player.onGround() && player.getDeltaMovement().y < 0) {
                    // Accumulate fall distance from downward velocity (blocks/tick)
                    tracked += (float) (-player.getDeltaMovement().y);
                    creativeFallDistance.put(uuid, tracked);
                } else if (player.onGround() && tracked > 1.5f && player.isShiftKeyDown()) {
                    performSlam(serverPlayer, tracked, powerLevel);
                    creativeFallDistance.put(uuid, 0f);
                } else {
                    creativeFallDistance.put(uuid, 0f);
                }
            }
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    /** The slam, shared by the survival LivingFallEvent handler and the creative tick tracker. */
    public static void performSlam(ServerPlayer player, float fallDistance, int powerLevel) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        player.setSpawnExtraParticlesOnFall(true);
        SoundEvent sound = fallDistance > 5.0F
                ? BeyondSoundEvents.MACE_SMASH_GROUND_HEAVY.get() : BeyondSoundEvents.MACE_SMASH_GROUND.get();
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, player.getSoundSource(), 1.0F, 1.0F);

        AOEManager.knockback(serverLevel, player, player, powerLevel + 1);
    }

    /** Called from {@link com.thebeyond.common.event.BeyondCoreLifecycle} on server stop. */
    public static void clearCreativeTracking() {
        creativeFallDistance.clear();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.literal("When Crouching:").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal(" Perform Slam Attack").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(Component.literal(" + Falling Speed").withStyle(ChatFormatting.BLUE));
    }
}
