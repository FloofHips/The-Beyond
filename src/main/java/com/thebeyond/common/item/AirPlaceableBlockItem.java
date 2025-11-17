package com.thebeyond.common.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AirPlaceableBlockItem extends BlockItem {
    public AirPlaceableBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        //BlockHitResult result = new BlockHitResult()
        //BlockPlaceContext context = new BlockPlaceContext(player, usedHand, player.getItemInHand(usedHand), );
        int lowest = player.level().getMinBuildHeight();
        int highest = player.level().getMaxBuildHeight();

        //if (player.position().y < lowest - 1) return InteractionResultHolder.fail(player.getItemInHand(usedHand));

        Vec3 hitVec = player.getEyePosition().add(player.getLookAngle().scale(5));
        BlockHitResult hit = level.isBlockInLine(new ClipBlockStateContext(
                player.getEyePosition(),
                new Vec3(hitVec.x, Math.clamp(hitVec.y, lowest, highest), hitVec.z),
                state -> state.isAir()
        ));


        if (hit != null) {
            ItemStack stack = player.getItemInHand(usedHand);
            InteractionResult i = this.place(new BlockPlaceContext(player, usedHand, stack, hit));
            return new InteractionResultHolder<>(i, stack);
        }

        return super.use(level, player, usedHand);
    }
}
