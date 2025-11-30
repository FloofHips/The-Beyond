package com.thebeyond.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
public class LinerItem extends DevBuildingItem {
    public LinerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockPos playerpos = player.blockPosition().offset(0, -1,0);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        HitResult hitResult = player.pick(64.0, 0.0F, false);

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            Vec3 lookVec = player.getLookAngle();
            BlockPos endPos = playerpos.offset(
                    (int)(lookVec.x * 50),
                    (int)(lookVec.y * 50),
                    (int)(lookVec.z * 50)
            );
            drawLine(level, playerpos, endPos, getPlacementBlock(player));
        } else {
            BlockPos endPos = ((BlockHitResult)hitResult).getBlockPos();
            drawLine(level, playerpos, endPos, getPlacementBlock(player));
        }

        playPlaceSound(level, player.blockPosition());
        spawnParticles(level, player.blockPosition());

        return InteractionResultHolder.success(stack);
    }

    private void drawLine(Level level, BlockPos start, BlockPos end, BlockState block) {
        Vec3 startVec = new Vec3(start.getX(), start.getY(), start.getZ());
        Vec3 endVec = new Vec3(end.getX(), end.getY(), end.getZ());
        Vec3 direction = endVec.subtract(startVec);
        double distance = startVec.distanceTo(endVec);

        for (double d = 0; d <= distance; d += 0.5) {
            Vec3 current = startVec.add(direction.normalize().scale(d));
            BlockPos linePos = new BlockPos(
                    (int)Math.round(current.x),
                    (int)Math.round(current.y),
                    (int)Math.round(current.z)
            );

            if (level.isEmptyBlock(linePos)) {
                level.setBlock(linePos, block, 3);
            }
        }
    }
}