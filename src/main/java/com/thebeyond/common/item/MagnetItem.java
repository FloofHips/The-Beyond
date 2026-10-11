package com.thebeyond.common.item;

import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.client.particle.CircleColorTransitionOptions;
import com.thebeyond.client.particle.CrosshairColorTransitionOptions;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.*;
import com.thebeyond.util.ColorUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;

import java.util.List;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import org.joml.Vector3f;

public class MagnetItem extends Item {
    public final double range;

    public MagnetItem(Properties properties, double range) {
        super(properties);
        this.range = range;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);

        double range = 32;
        Vec3 eyePos = player.getEyePosition();
        Vec3 endPos = eyePos.add(player.getLookAngle().scale(range));

        ClipContext clipContext = new ClipContext(
                eyePos,
                endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        );

        BlockHitResult hit = level.clip(clipContext);

        if (hit.getType() != HitResult.Type.MISS) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);

            if (state.is(BeyondTags.METAL_BLOCKS)) {

                Vec3 playerPos = player.position();
                Vec3 blockCenter = BeyondCompatHooks.visibleOrCenter(level, pos);
                Vec3 distance = blockCenter.subtract(playerPos);
                Vec3 direction = distance.normalize();

                player.setDeltaMovement(direction.scale(1.5));
                player.hurtMarked = true;

                level.addParticle(new CircleColorTransitionOptions(
                        new Vector3f(1, 1, 1),
                        new Vector3f(0.7f, 0.0f, 0.9f),
                        (float) (0.1*distance.length())
                ), blockCenter.x, blockCenter.y, blockCenter.z, 0, 0, 0);

                level.addParticle(new CrosshairColorTransitionOptions(
                        new Vector3f(0.7f, 0.0f, 0.9f),
                        new Vector3f(0.1f, 0.1f, 0.3f),
                        (float) (0.2*distance.length()/15f)
                ), blockCenter.x+0.001f, blockCenter.y, blockCenter.z+0.001f, 0, 0, 0);


                if (level instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 15; i++) {
                        double lerp = i / 15.0;
                        Vec3 particlePos = playerPos.add(0,1,0).lerp(blockCenter, lerp);
                        level.playSound(null, player.getX(), player.getY(), player.getZ(), BeyondSoundEvents.PULL.get(), SoundSource.NEUTRAL, 1f, (float) i/7.5f);
                        serverLevel.sendParticles(new CircleColorTransitionOptions(
                                new Vector3f(0.7f, 0.0f, 0.9f),
                                new Vector3f(1, 1, 1),
                                ((float) (lerp*lerp) + 0.05f) * 0.5f
                        ), particlePos.x, particlePos.y, particlePos.z, 1, 0, 0, 0, 0);
                    }

                    if (distance.length() > 31 && player instanceof ServerPlayer serverPlayer) BeyondCriteriaTriggers.FULL_POWER_MAGNET.get().trigger(serverPlayer);
                }

                level.playSound(null, player.getX(), player.getY(), player.getZ(), BeyondSoundEvents.MAGNET_SUCCESS.get(), SoundSource.NEUTRAL, 1f, 0.5f + level.random.nextFloat());

                player.awardStat(Stats.ITEM_USED.get(this));
                player.getCooldowns().addCooldown(this, 10);
                return InteractionResultHolder.success(itemstack);
            } else {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), BeyondSoundEvents.MAGNET_FAIL.get(), SoundSource.PLAYERS, 1, 0.5f + level.random.nextFloat());
                return InteractionResultHolder.consume(itemstack);
            }
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), BeyondSoundEvents.MAGNET_FAIL.get(), SoundSource.PLAYERS, 1, 0.5f + level.random.nextFloat());
            return InteractionResultHolder.consume(itemstack);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {

        if (slotId > 8 && slotId != 40) return;

        double halfRange = range/2;
        level.getEntitiesOfClass(ItemEntity.class, new AABB(entity.position().subtract(halfRange, halfRange, halfRange), entity.position().add(halfRange, halfRange, halfRange)))
                .forEach(itemEntity -> {
                            if (itemEntity.tickCount > 20) itemEntity.setNoPickUpDelay();
                            double dist = itemEntity.position().vectorTo(entity.position()).length();
                            if (dist < 0.01) return;
                            double factor = 0.01 * range / dist;
                            itemEntity.addDeltaMovement(itemEntity.position().vectorTo(entity.position()).multiply(factor, factor, factor));
                        }
                );
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.literal("On Use:").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal(" Slingshot towards metal").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(Component.literal("Passive:").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal(" Attract nearby items").withStyle(ChatFormatting.BLUE));
    }
}
