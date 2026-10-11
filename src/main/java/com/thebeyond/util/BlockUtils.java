package com.thebeyond.util;

import com.thebeyond.common.block.blockstates.RakedProperty;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import static com.thebeyond.common.block.RakedNacreBlock.*;

public class BlockUtils {

    public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        Direction tailDirection = player.getDirection().getOpposite();
        Direction headDirection = getHitDirection(hitResult);
        RakedProperty property = getRakedProperty(headDirection, tailDirection);

        if (property != null) {
            BlockState newState = state.setValue(RAKE_DIRECTION, property);
            if (newState == state) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

            if (player instanceof ServerPlayer serverPlayer) {
                BeyondCriteriaTriggers.RAKE_NACRE.get().trigger(serverPlayer);
            }

            level.setBlock(pos, newState, 3);
            level.playSound(player, pos, BeyondSoundEvents.NACRE_RAKE.get(), SoundSource.BLOCKS, 1.0F, 0.8f + level.random.nextFloat());
            level.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 0.8f + level.random.nextFloat());
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));

            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
