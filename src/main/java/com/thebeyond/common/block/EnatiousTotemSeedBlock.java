package com.thebeyond.common.block;

import com.thebeyond.common.entity.EnatiousTotemEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class EnatiousTotemSeedBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public EnatiousTotemSeedBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(POWERED);
    }

    public boolean isActive(BlockState state) {
        return state.getValue(POWERED);
    }

    public void activate(BlockState state, Level level, BlockPos pos) {
        activate(state, level, pos, null);
    }

    public void activate(BlockState state, Level level, BlockPos pos, LivingEntity target) {
        if (isActive(state)) return;

        EnatiousTotemEntity totem = new EnatiousTotemEntity(BeyondEntityTypes.ENATIOUS_TOTEM.get(), level);
        totem.setPos(pos.getX() + 0.5, pos.getY()+1, pos.getZ() + 0.5);

        if (level instanceof ServerLevel serverLevel){
            serverLevel.addFreshEntity(totem);
            serverLevel.setBlockAndUpdate(pos, state.setValue(POWERED, true));
        }

        if (target != null) totem.setTarget(target);
        totem.spawn();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        activate(state, level, pos, player);
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (random.nextFloat() < 0.2) level.setBlockAndUpdate(pos, state.setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
//public class EnatiousTotemSeed extends Block implements GameEventListener {
//    private BlockPos listenerPos = BlockPos.ZERO;
//    @Nullable
//    private ServerLevel currentLevel;
//
//    public EnatiousTotemSeed(Properties properties) {
//        super(properties);
//    }
//
//    @Override
//    public PositionSource getListenerSource() {
//        return new BlockPositionSource(listenerPos);
//    }
//
//    @Override
//    public int getListenerRadius() {
//        return 16;
//    }
//
//    @Override
//    public boolean handleGameEvent(ServerLevel serverLevel, Holder<GameEvent> holder, GameEvent.Context context, Vec3 vec3) {
//        if (context.sourceEntity() instanceof Player player) {
//            //if (cow.distanceToSqr(listenerPos.getX(), listenerPos.getY(), listenerPos.getZ()) <= getListenerRadius() * getListenerRadius()) {
//                serverLevel.playSound(null, listenerPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
//                spawnParticles(serverLevel, player.position(), true);
//                spawnParticles(serverLevel, Vec3.atLowerCornerOf(listenerPos), false);
//                player.push(0,1,0);
//                player.hurtMarked = true;
//                return true;
//            //}
//        }
//        return false;
//    }
//
//    private void spawnParticles(ServerLevel level, Vec3 pos, boolean f) {
//        if(f)
//            for (int i = 0; i < 10; i++) {
//                double x = pos.x + (level.random.nextDouble() - 0.5) * 2.0;
//                double y = pos.y + level.random.nextDouble() * 2.0;
//                double z = pos.z + (level.random.nextDouble() - 0.5) * 2.0;
//                level.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0, 0, 0, 0.1);
//            }
//        else
//            for (int i = 0; i < 5; i++) {
//                double x = pos.x + (level.random.nextDouble() - 0.5) * 1.5;
//                double y = pos.y + level.random.nextDouble() * 1.5;
//                double z = pos.z + (level.random.nextDouble() - 0.5) * 1.5;
//                level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0.05);
//            }
//    }
//
//    @Override
//    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
//        super.onPlace(state, level, pos, oldState, isMoving);
//        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
//            setListenerPosition(pos, serverLevel);
//            registerListener(serverLevel);
//        }
//    }
//
//    @Override
//    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
//        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
//            unregisterListener(serverLevel);
//        }
//        super.onRemove(state, level, pos, newState, isMoving);
//    }
//
//    private void setListenerPosition(BlockPos pos, ServerLevel level) {
//        this.listenerPos = pos;
//        this.currentLevel = level;
//    }
//
//    private void registerListener(ServerLevel level) {
//        level.getChunk(listenerPos).getListenerRegistry(listenerPos.getY()/16).register(this);
//    }
//
//    private void unregisterListener(ServerLevel level) {
//        level.getChunk(listenerPos).getListenerRegistry(listenerPos.getY()/16).unregister(this);
//    }
//
//    @Override
//    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
//        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
//        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
//            if (!pos.equals(listenerPos)) {
//                unregisterListener(serverLevel);
//                setListenerPosition(pos, serverLevel);
//                registerListener(serverLevel);
//            }
//        }
//    }
//}