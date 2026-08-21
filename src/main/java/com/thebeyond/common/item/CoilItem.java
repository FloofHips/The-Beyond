package com.thebeyond.common.item;

import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.client.particle.CrosshairColorTransitionOptions;
import com.thebeyond.common.entity.CoilEntity;
import com.thebeyond.common.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class CoilItem extends Item {
    public CoilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);

        BlockHitResult result = rayCast(level, player);
        if (result !=null) {
            BlockPos pos = result.getBlockPos();
            Direction dir = result.getDirection();
//
            Vec3 blockCenter = BeyondCompatHooks.visibleOrCenter(level, pos.offset(dir.getStepX(), dir.getStepY(), dir.getStepZ()));
            BlockPos blockPos = BlockPos.containing(blockCenter);
//
            //for (int i = 0; i < 30; i++) {
            //    level.setBlock(blockPos.offset(dir.getStepX()*i, dir.getStepY()*i, dir.getStepZ()*i), BeyondBlocks.COIL_VERTEBRAE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, dir.getAxis()),3);
            //}

            if (!level.isClientSide) {
                CoilEntity coil = new CoilEntity(BeyondEntityTypes.COILED_STALK.get(), level, dir, blockPos);

                coil.setPos(player.getX(), player.getY()+1, player.getZ());
                coil.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 0.0F);
                level.addFreshEntity(coil);
            }
        }



        player.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, player);

        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.success(itemstack);
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
            return hit;
        }

        return null;
    }


    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!isSelected) return;

        if (entity.level().isClientSide && entity.tickCount % 15 == 0) {
            BlockHitResult result = rayCast(level, entity);
            if (result !=null) {
                BlockPos pos = result.getBlockPos();
                Direction dir = result.getDirection();

                Vec3 blockCenter = BeyondCompatHooks.visibleOrCenter(level, pos.offset(dir.getStepX(), dir.getStepY(), dir.getStepZ()));

                level.addParticle(new CrosshairColorTransitionOptions(
                        new Vector3f(0.7f, 0.0f, 0.9f),
                        new Vector3f(0.1f, 0.1f, 0.3f),
                        0.2f
                ), true, blockCenter.x+0.001f, blockCenter.y, blockCenter.z+0.001f, 0, 0, 0);
            }
        }
    }
}
