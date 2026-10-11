package com.thebeyond.common.item;

import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.client.particle.CrosshairColorTransitionOptions;
import com.thebeyond.common.entity.CoilEntity;
import com.thebeyond.common.registry.*;
import com.thebeyond.util.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.joml.Vector3f;

import java.util.List;

public class CoilItem extends Item {
    public CoilItem(Properties properties) {
        super(properties);
    }

    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (entityLiving instanceof Player player) {
            BlockHitResult result = rayCast(level, player);
            if (result !=null) {
                BlockPos pos = result.getBlockPos();
                Direction dir = result.getDirection();

                Vec3 blockCenter = BeyondCompatHooks.visibleOrCenter(level, pos.offset(dir.getStepX(), dir.getStepY(), dir.getStepZ()));
                BlockPos blockPos = BlockPos.containing(blockCenter);
                level.playSound((Player)null, player.getX(), player.getY(), player.getZ(), BeyondSoundEvents.ITEM_THROW.get(), SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

                if (!level.isClientSide) {
                    CoilEntity coil = new CoilEntity(BeyondEntityTypes.COILED_STALK.get(), level, dir, blockPos);

                    coil.setPos(player.getX(), player.getY()+1, player.getZ());
                    coil.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2F, 0F);
                    level.addFreshEntity(coil);
                }
            }

            stack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
            player.getCooldowns().addCooldown(this, 10);
        }

    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        BlockHitResult result = rayCast(level, player);
        if (result == null) {
            return InteractionResultHolder.fail(itemstack);
        } else {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(itemstack);
        }
    }

    private static BlockHitResult rayCast(Level level, Entity entity) {
        double range = 64;
        if (!(entity instanceof LivingEntity livingEntity)) return null;

        Vec3 eyePos = livingEntity.getEyePosition();
        Vec3 endPos = eyePos.add(livingEntity.getLookAngle().scale(range));

        ClipContext clipContext = new ClipContext(
                eyePos,
                endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                livingEntity
        );

        BlockHitResult hit = level.clip(clipContext);

        if (hit.getType() != HitResult.Type.MISS) {
            if (level.getBlockState(hit.getBlockPos()).is(BeyondBlocks.AURORACITE)) return null;
            return hit;
        }

        return null;
    }


    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!isSelected) return;

        if (entity.level().isClientSide && entity.tickCount % 10 == 0) {
            BlockHitResult result = rayCast(level, entity);
            if (result !=null) {
                BlockPos pos = result.getBlockPos();
                Direction dir = result.getDirection().getOpposite();

                Vec3 blockCenter = BeyondCompatHooks.visibleOrCenter(level, pos.offset(dir.getStepX(), dir.getStepY(), dir.getStepZ()));

                RenderUtils.spawnBlockFaceParticle(level, dir, pos);
            }
        }
    }
}
