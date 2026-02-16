package com.thebeyond.common.block;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.common.block.blockentities.BonfireBlockEntity;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Optional;

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
            if (stack.is(BeyondItems.LIVID_FLAME.asItem()) || stack.is(BeyondItems.LIVE_FLAME.asItem())) {
                if (!player.isCreative()) stack.shrink(1);

                level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1, 1);
                level.setBlockAndUpdate(pos, state.setValue(LIT, true));

                if (level instanceof ServerLevel serverLevel)
                    serverLevel.sendParticles(getParticle(level), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.25, 1, 0.25, 0.015);
                BlockEntity bonfire = level.getBlockEntity(pos);
                if (bonfire instanceof BonfireBlockEntity bonfireBlockEntity) {
                    bonfireBlockEntity.activate(player);
                }
                return ItemInteractionResult.CONSUME;
            }
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        } else {
            if (stack.is(BeyondItems.ECTOPLASM.asItem())) {
                if (!player.isCreative()) stack.shrink(1);

                ItemStack flameStack = new ItemStack(level.isRaining() ? BeyondItems.LIVID_FLAME.asItem() : BeyondItems.LIVE_FLAME.asItem(),1);
                Components.DynamicColorComponent colors = new Components.DynamicColorComponent(1, 1, 1, 1, 0, 0, 0, 0, 0xF000F0);
                flameStack.set(BeyondComponents.COLOR_COMPONENT, colors);
                player.addItem(flameStack);

                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1, 1);

                return ItemInteractionResult.CONSUME;
            }
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
    }

    public SimpleParticleType getParticle(Level level) {
        return level.isRaining() ? BeyondParticleTypes.VOID_FLAME.get() : ParticleTypes.SOUL_FIRE_FLAME;
    }

    public static boolean isOpposite(BlockState thisState, BlockState potentialState) {
        return thisState.getValue(LIT) == !potentialState.getValue(LIT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            Optional<BlockPos> sisterBonfire = findNearestBonfire(serverLevel, state, pos, 200);
            BlockPos sisterStructure = serverLevel.findNearestMapStructure(BeyondTags.BONFIRE_LOCATABLE, pos, 500, true);

            if (sisterBonfire.isPresent()) {
                sendBeam(level, pos, player, serverLevel, sisterBonfire.get(), false);
                return InteractionResult.CONSUME;
            } else {
                if (sisterStructure != null) {
                    sendBeam(level, pos, player, serverLevel, sisterStructure, true);
                    return InteractionResult.CONSUME;
                } else {
                    level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.CONSUME;
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    private static void sendBeam(Level level, BlockPos pos, Player player, ServerLevel serverLevel, BlockPos sisterPos, boolean structure) {

        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, sisterPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);

        particleBeam(serverLevel, player, pos, sisterPos, structure);
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

    public static void particleBeam(Level level, Player player, BlockPos from, BlockPos to, boolean structure) {
        float red = structure ? 1 : 0.1F + level.random.nextFloat() * 0.1F;
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

                Vector3f color = new Vector3f(red, green, blue);
                ParticleOptions options = new DustParticleOptions(color, scale);

                serverLevel.sendParticles(serverPlayer, options, true, pos.x, pos.y, pos.z, 1, 0,0,0,0.000);
            }
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(LIT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        BlockEntity bonfire = level.getBlockEntity(pos);
        if (bonfire instanceof BonfireBlockEntity bonfireBlockEntity) {
            if (bonfireBlockEntity.isItMyBirthdayToday()) {
                level.setBlockAndUpdate(pos, state.setValue(LIT, false));
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            for (Direction d : Direction.values()) {
                if (d == Direction.UP || d == Direction.DOWN) continue;
                spawnBabyFlames(level, Vec3.atCenterOf(pos).add(d.getStepX()*0.7, 0.2, d.getStepZ()*0.7), random);
            }
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, (double)pos.getX() + (double)0.5F + random.nextDouble() / (double)3.0F * (double)(random.nextBoolean() ? 1 : -1), (double)pos.getY() + random.nextDouble() + random.nextDouble(), (double)pos.getZ() + (double)0.5F + random.nextDouble() / (double)3.0F * (double)(random.nextBoolean() ? 1 : -1), (double)0.0F, 0.07, (double)0.0F);
        }
        super.animateTick(state, level, pos, random);
    }

    public void spawnBabyFlames(Level level, Vec3 pos, RandomSource random) {
        for (int i = 0; i < random.nextInt(4,8); i++) {
            level.addParticle(getParticle(level), pos.x + ((random.nextFloat() - 0.5) * 0.25), pos.y + ((random.nextFloat() - 0.5) * 0.25), pos.z + ((random.nextFloat() - 0.5) * 0.25), (double) 0.001F, (double) 0.01F, (double) 0.001F);
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
