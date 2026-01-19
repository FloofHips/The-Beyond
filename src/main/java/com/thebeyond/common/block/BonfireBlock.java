package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockentities.BonfireBlockEntity;
import com.thebeyond.common.block.blockentities.MemorFaucetBlockEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondPoiTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.ParticleUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BonfireBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BonfireBlock(Properties properties) {
        super(properties);
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{LIT});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(LIT, false);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) ? 15 : 0;
    }

    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (level.isClientSide) return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (!state.getValue(LIT)) {
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1, 1);
            level.setBlockAndUpdate(pos, state.setValue(LIT, true));
            if (level instanceof ServerLevel serverLevel)
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.25, 1, 0.25, 0.015);
            BlockEntity bonfire = level.getBlockEntity(pos);
            if (bonfire instanceof BonfireBlockEntity bonfireBlockEntity) {
                bonfireBlockEntity.activate(player);
            }
            return ItemInteractionResult.CONSUME;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    public static boolean isOpposite(BlockState thisState, BlockState potentialState) {
        return thisState.getValue(LIT) == !potentialState.getValue(LIT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            Optional<BlockPos> sisterBonfire = findNearestBonfire(serverLevel, state, pos, 200);

            if (sisterBonfire.isPresent()) {
                BlockPos sisterPos = sisterBonfire.get();

                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.playSound(null, sisterPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);

                particleBeam(serverLevel, player, pos, sisterPos);

                return InteractionResult.CONSUME;
            } else {
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.SUCCESS;
    }

    public static Optional<BlockPos> findNearestBonfire(ServerLevel level, BlockState sourceState, BlockPos sourcePos, int radius) {
        return level.getPoiManager().findClosest(
                poiType -> poiType.is(BeyondPoiTypes.BONFIRE),
                blockPos -> {
                    BlockState state = level.getBlockState(blockPos);
                    return state.is(BeyondBlocks.BONFIRE.get()) && isOpposite(state, sourceState);
                },
                sourcePos,
                radius,
                PoiManager.Occupancy.ANY
        );
    }

    public static void particleBeam(Level level, Player player, BlockPos from, BlockPos to) {
        float red = 0.1F + level.random.nextFloat() * 0.1F;
        float green = 0.4F + level.random.nextFloat() * 0.3F;
        float blue = 0.8F + level.random.nextFloat() * 0.2F;
        float scale = 1F + level.random.nextFloat() * 0.3F;

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Vec3 start = Vec3.atCenterOf(from);
            Vec3 end = Vec3.atCenterOf(to);
            Vec3 diff = end.subtract(start);

            for (int i = 0; i < diff.length(); i++) {
                double progress = (i + level.random.nextDouble()) / diff.length();
                Vec3 pos = start.add(diff.scale(progress));

                pos = pos.add(
                        (level.random.nextDouble() - 0.5) * 0.1,
                        (level.random.nextDouble() - 0.5) * 0.1,
                        (level.random.nextDouble() - 0.5) * 0.1
                );

                ParticleOptions options = new DustParticleOptions(new Vector3f(red, green, blue), scale);

                serverLevel.sendParticles(serverPlayer, options, true, pos.x, pos.y, pos.z, 2, 0.3,0,0.3,0.005);
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new BonfireBlockEntity(blockPos, blockState);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.create(0, 0, 0, 1, 0.5, 1);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BeyondBlockEntities.BONFIRE.get(), BonfireBlockEntity::tick);
    }
}
