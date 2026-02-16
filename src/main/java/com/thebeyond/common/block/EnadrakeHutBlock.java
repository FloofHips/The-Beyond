package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockentities.BonfireBlockEntity;
import com.thebeyond.common.block.blockentities.EnadrakeHutBlockEntity;
import com.thebeyond.common.block.blockentities.MemorFaucetBlockEntity;
import com.thebeyond.common.block.blockstates.HutHeightProperty;
import com.thebeyond.common.block.blockstates.PillarHeightProperty;
import com.thebeyond.common.fluid.GellidVoidBlock;
import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class EnadrakeHutBlock extends BaseEntityBlock {
    public static final MapCodec<EnadrakeHutBlock> CODEC = simpleCodec(EnadrakeHutBlock::new);
    public static final EnumProperty<HutHeightProperty> HEIGHT;
    public static final DirectionProperty FACING;

    protected BlockState rotate(BlockState state, Rotation rot) {
        return (BlockState)state.setValue(FACING, rot.rotate((Direction)state.getValue(FACING)));
    }

    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction)state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public EnadrakeHutBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(HEIGHT, HutHeightProperty.TIP)));

    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity hut = level.getBlockEntity(pos);
        if (hut instanceof EnadrakeHutBlockEntity hutblockentity) {
            if (level.isClientSide) {
                return ItemInteractionResult.CONSUME;
            } else {
                ItemStack itemstack1 = hutblockentity.getTheItem();
                if (!stack.isEmpty() && (itemstack1.isEmpty() && itemstack1.getCount() < itemstack1.getMaxStackSize())) {
                    fillHut(stack, level, pos, player, hutblockentity, itemstack1);
                    return ItemInteractionResult.SUCCESS;
                } else {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        } else {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
    }

    public static void fillHut(ItemStack stack, Level level, BlockPos pos, LivingEntity entity, EnadrakeHutBlockEntity hutblockentity, ItemStack itemstack1) {
        ItemStack itemstack = stack.consumeAndReturn(1, entity);
        float f;
        if (hutblockentity.isEmpty()) {
            hutblockentity.setTheItem(itemstack);
            f = (float)itemstack.getCount() / (float)itemstack.getMaxStackSize();
        } else {
            itemstack1.grow(1);
            f = (float) itemstack1.getCount() / (float) itemstack1.getMaxStackSize();
        }

        level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F * f);
        if (level instanceof ServerLevel serverlevel) {
            serverlevel.sendParticles(
                    ParticleTypes.DUST_PLUME,
                    (double) pos.getX() + 0.5,
                    (double) pos.getY() + 1.2,
                    (double) pos.getZ() + 0.5,
                    7,
                    0.0,
                    0.0,
                    0.0,
                    0.0
            );
        }

        hutblockentity.setChanged();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, HEIGHT});
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if(state.getValue(HEIGHT) == HutHeightProperty.TIP)
            return TIP_SHAPE;
        return CORE_SHAPE;
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();

        if (context.getLevel().getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock) {
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HEIGHT, HutHeightProperty.TOP);
        }

        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HEIGHT, HutHeightProperty.TIP);

    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {

        if (!(level.getBlockState(pos.above()).getBlock() instanceof EnadrakeHutBlock)){
            if (level.getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock) {
                return state.setValue(HEIGHT, HutHeightProperty.TOP);
            }
            return state.setValue(HEIGHT, HutHeightProperty.TIP);
        }
        else {
            if (level.getBlockState(pos.above()).getBlock() instanceof EnadrakeHutBlock && level.getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock) {
                return state.setValue(HEIGHT, HutHeightProperty.CORE);
            }
            if (!(level.getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock)) {
                return state.setValue(HEIGHT, HutHeightProperty.BASE);
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new EnadrakeHutBlockEntity(blockPos, blockState);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BeyondBlockEntities.ENADRAKE_HUT.get(), EnadrakeHutBlockEntity::tick);
    }

    private static final VoxelShape TIP_SHAPE;
    private static final VoxelShape CORE_SHAPE;

    static {
        FACING = BlockStateProperties.HORIZONTAL_FACING;
        HEIGHT = EnumProperty.create("height", HutHeightProperty.class);
        TIP_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 9.0, 15.0);
        CORE_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
    }
}
