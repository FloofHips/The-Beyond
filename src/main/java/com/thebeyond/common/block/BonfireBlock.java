package com.thebeyond.common.block;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.common.block.blockentities.BonfireBlockEntity;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.network.ShowBonfireTutorialToastPacket;
import com.thebeyond.common.registry.*;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Optional;

import static com.thebeyond.common.block.ProjectorBlock.FACING;

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

                level.playSound(null, pos,  BeyondSoundEvents.BONFIRE_ACTIVATE.get(), SoundSource.BLOCKS, 1, 1);
                level.setBlockAndUpdate(pos, state.setValue(LIT, true));

                if (level instanceof ServerLevel serverLevel)
                    serverLevel.sendParticles(getParticle(level), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.25, 1, 0.25, 0.015);
                BlockEntity bonfire = level.getBlockEntity(pos);
                if (bonfire instanceof BonfireBlockEntity bonfireBlockEntity) {
                    bonfireBlockEntity.activate(player);
                }
                if (player instanceof ServerPlayer serverPlayer) {
                    BeyondCriteriaTriggers.LIGHT_BONFIRE.get().trigger(serverPlayer);
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

                level.playSound(null, pos, BeyondSoundEvents.BONFIRE_IGNITE.get(), SoundSource.BLOCKS, 1, 0.8f + level.random.nextFloat()*0.3f);

                if (player instanceof ServerPlayer serverPlayer) {
                    if (level.isRaining()) {
                        BeyondCriteriaTriggers.OBTAIN_LIVID_FLAME.get().trigger(serverPlayer);
                    } else {
                        BeyondCriteriaTriggers.OBTAIN_LIVE_FLAME.get().trigger(serverPlayer);
                    }
                }

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

            if (player instanceof ServerPlayer serverPlayer)
                if (serverPlayer.level().getServer() != null) {
                    ResourceLocation recipeAdvancementId = ResourceLocation.fromNamespaceAndPath("the_beyond", "recipes/misc/ectoplasm");
                    AdvancementHolder holder = serverPlayer.server.getAdvancements().get(recipeAdvancementId);

                    if (holder != null) {
                        AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(holder);
                        for (String criterion : progress.getRemainingCriteria()) {
                            serverPlayer.getAdvancements().award(holder, criterion);
                        }
                    }
                }

            if (sisterBonfire.isPresent()) {
                player.displayClientMessage(Component.translatable("block.bonfire.found"),true);
                sendBeam(level, pos, player, serverLevel, sisterBonfire.get(), false);
                return InteractionResult.CONSUME;
            } else {
                BlockPos sisterStructure = serverLevel.findNearestMapStructure(BeyondTags.BONFIRE_LOCATABLE, pos, 500, true);

                if (sisterStructure != null) {
                    player.displayClientMessage(Component.translatable("block.bonfire.near"),true);
                    sendBeam(level, pos, player, serverLevel, sisterStructure, true);
                    return InteractionResult.CONSUME;
                } else {
                    player.displayClientMessage(Component.translatable("block.bonfire.none"),true);
                    level.playSound(null, pos,  BeyondSoundEvents.BONFIRE_SEARCH.get(), SoundSource.BLOCKS, 1.0F, 0.8f + level.random.nextFloat()*0.3f);
                    return InteractionResult.CONSUME;
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    private static void sendBeam(Level level, BlockPos pos, Player player, ServerLevel serverLevel, BlockPos sisterPos, boolean structure) {

        level.playSound(null, pos, BeyondSoundEvents.BONFIRE_SEARCH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, sisterPos, BeyondSoundEvents.BONFIRE_SEARCH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);

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
        if (player instanceof ServerPlayer serverPlayer) {
            Vec3 start = Vec3.atCenterOf(from);
            Vec3 end = Vec3.atCenterOf(to);
            Vec3 diff = end.subtract(start);
            Vec3 dir = end.subtract(start).normalize();

            if (structure) {
                for (int j = 0; j < 10; j++) {
                    level.addParticle(BeyondParticleTypes.SOUL.get(), start.x + 1 - level.random.nextFloat() * 2, start.y + 1 - level.random.nextFloat() * 2, start.z + 1 - level.random.nextFloat() * 2, 0.0f, (double) 0.01F, 0.0f);

                    ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                            BeyondParticleTypes.SOUL.get(),
                            false,
                            start.x + 2 - level.random.nextFloat() * 4, start.y + level.random.nextFloat(), start.z + 2 - level.random.nextFloat() * 4,
                            0,
                            0.5f + level.random.nextFloat(),
                            0,
                            0.15F,
                            0
                    );

                    serverPlayer.connection.send(packet);
                }
                return;
            }

            for (int i = 0; i < diff.length(); i++) {
                double progress = (i + level.random.nextDouble()) / diff.length();
                Vec3 pos = start.add(diff.scale(progress));

                pos = pos.add(
                        (level.random.nextDouble() - 0.5) * 0.1,
                        (level.random.nextDouble() - 0.5) * 0.1,
                        (level.random.nextDouble() - 0.5) * 0.1
                );

                float v = 0.5f + level.random.nextFloat();
                ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                        BeyondParticleTypes.SOUL.get(),
                        true,
                        pos.x, pos.y + level.random.nextFloat(), pos.z,
                        (float) dir.x * v,
                        (float) dir.y * v,
                        (float) dir.z * v,
                        0.2F,
                        0
                );

                serverPlayer.connection.send(packet);
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
            if (random.nextInt(5) == 0) level.playLocalSound((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)0.5F, (double)pos.getZ() + (double)0.5F, level.isRaining() ? BeyondSoundEvents.BONFIRE_IDLE_CORRUPTED.get() : BeyondSoundEvents.BONFIRE_IDLE.get(), SoundSource.BLOCKS, 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);

            for (Direction d : Direction.values()) {
                if (d == Direction.UP || d == Direction.DOWN) continue;
                spawnBabyFlames(level, Vec3.atCenterOf(pos).add(d.getStepX()*0.7, 0.2, d.getStepZ()*0.7), random);
            }

            if (random.nextInt(10) == 0) level.addParticle(BeyondParticleTypes.SOUL.get(), pos.getX() + (1 - random.nextFloat())*2*(random.nextBoolean() ? 1 : -1), pos.getY(), pos.getZ() + (1 - random.nextFloat())*2*(random.nextBoolean() ? 1 : -1), (double) 0.001F, (double) 0.1F, (double) 0.001F);
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
