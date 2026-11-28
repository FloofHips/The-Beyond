package com.thebeyond.common.block;

import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.worldgen.OrganicDeath;
import com.thebeyond.common.worldgen.OrganicFire;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Placeholder for a bulb: closed it cannot be broken, once light exposes it it can, and regrown tissue drops nothing. */
public class OrganicBulbBlock extends Block {
    public static final BooleanProperty EXPOSED = BooleanProperty.create("exposed");
    public static final BooleanProperty REGROWN = BooleanProperty.create("regrown");
    // the organ's block nearest its centre, the one whose breaking kills it
    public static final BooleanProperty CORE = BooleanProperty.create("core");
    public static final float BULB_HARDNESS = Float.parseFloat(System.getProperty("beyond.sub.org.bulb.hardness", "1.5"));
    public static final float NUCLEUS_HARDNESS = Float.parseFloat(System.getProperty("beyond.sub.org.nucleus.hardness", "1.5"));

    public OrganicBulbBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(EXPOSED, false).setValue(REGROWN, false).setValue(CORE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(EXPOSED, REGROWN, CORE);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(EXPOSED) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return state.getValue(EXPOSED) ? super.getExplosionResistance(state, level, pos, explosion) : 3_600_000;
    }

    /** A live flame held to the bulb burns there for what is left of it and exposes the bulb. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(BeyondItems.LIVE_FLAME.asItem()) && !stack.is(BeyondItems.LIVID_FLAME.asItem())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!(level instanceof ServerLevel server)) return ItemInteractionResult.SUCCESS;
        return OrganicFire.flame(server, pos, stack, player) ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) OrganicDeath.bulbRemoved(server, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
