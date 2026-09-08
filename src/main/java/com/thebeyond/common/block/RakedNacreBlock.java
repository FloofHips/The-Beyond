package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockstates.RakedProperty;
import com.thebeyond.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

public class RakedNacreBlock extends Block {

    public static final MapCodec<RakedNacreBlock> CODEC = simpleCodec(RakedNacreBlock::new);
    public static final EnumProperty<RakedProperty> RAKE_DIRECTION;
    protected static final VoxelShape SHAPE;

    public RakedNacreBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(RAKE_DIRECTION, RakedProperty.NS));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_272634_) {
        p_272634_.add(RAKE_DIRECTION);
    }

    protected VoxelShape getShape(BlockState p_153143_, BlockGetter p_153144_, BlockPos p_153145_, CollisionContext p_153146_) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            if (!stack.is(ItemTags.HOES)) return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
            return BlockUtils.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    public static Direction getHitDirection(BlockHitResult hitResult) {
        BlockPos blockpos = hitResult.getBlockPos();
        Vec3 vec3 = hitResult.getLocation().subtract(blockpos.getX(), blockpos.getY(), blockpos.getZ());
        double x = vec3.x();
        double z = vec3.z();

        boolean aboveDiagonal1 = x < z;
        boolean aboveDiagonal2 = x + z < 1.0;

        if (aboveDiagonal1) {
            if (aboveDiagonal2) {
                return Direction.WEST;
            } else {
                return Direction.SOUTH;
            }
        } else {
            if (aboveDiagonal2) {
                return Direction.NORTH;
            } else {
                return Direction.EAST;
            }
        }
    }

    public static RakedProperty getRakedProperty(Direction head, Direction tail) {

        if (head == Direction.NORTH && tail == Direction.SOUTH) return RakedProperty.NS;
        if (head == Direction.SOUTH && tail == Direction.NORTH) return RakedProperty.NS;
        if (head == Direction.NORTH && tail == Direction.NORTH) return RakedProperty.NS;
        if (head == Direction.SOUTH && tail == Direction.SOUTH) return RakedProperty.NS;

        if (head == Direction.EAST && tail == Direction.WEST) return RakedProperty.EW;
        if (head == Direction.WEST && tail == Direction.EAST) return RakedProperty.EW;
        if (head == Direction.EAST && tail == Direction.EAST) return RakedProperty.EW;
        if (head == Direction.WEST && tail == Direction.WEST) return RakedProperty.EW;

        if (head == Direction.NORTH && tail == Direction.WEST) return RakedProperty.NW;
        if (head == Direction.WEST && tail == Direction.NORTH) return RakedProperty.NW;

        if (head == Direction.NORTH && tail == Direction.EAST) return RakedProperty.NE;
        if (head == Direction.EAST && tail == Direction.NORTH) return RakedProperty.NE;

        if (head == Direction.SOUTH && tail == Direction.WEST) return RakedProperty.SW;
        if (head == Direction.WEST && tail == Direction.SOUTH) return RakedProperty.SW;

        if (head == Direction.SOUTH && tail == Direction.EAST) return RakedProperty.SE;
        if (head == Direction.EAST && tail == Direction.SOUTH) return RakedProperty.SE;

        return RakedProperty.NS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        RakedProperty property = state.getValue(RAKE_DIRECTION);
        return switch (rotation) {
            case CLOCKWISE_90 -> switch (property) {
                case EW -> state.setValue(RAKE_DIRECTION, RakedProperty.NS);
                case NS -> state.setValue(RAKE_DIRECTION, RakedProperty.EW);
                case SE -> state.setValue(RAKE_DIRECTION, RakedProperty.SW);
                case SW -> state.setValue(RAKE_DIRECTION, RakedProperty.NW);
                case NE -> state.setValue(RAKE_DIRECTION, RakedProperty.SE);
                case NW -> state.setValue(RAKE_DIRECTION, RakedProperty.NE);
            };
            case CLOCKWISE_180 -> switch (property) {
                case EW -> state.setValue(RAKE_DIRECTION, RakedProperty.EW);
                case NS -> state.setValue(RAKE_DIRECTION, RakedProperty.NS);
                case SE -> state.setValue(RAKE_DIRECTION, RakedProperty.NW);
                case SW -> state.setValue(RAKE_DIRECTION, RakedProperty.NE);
                case NE -> state.setValue(RAKE_DIRECTION, RakedProperty.SW);
                case NW -> state.setValue(RAKE_DIRECTION, RakedProperty.SE);
            };
            case COUNTERCLOCKWISE_90 -> switch (property) {
                case EW -> state.setValue(RAKE_DIRECTION, RakedProperty.NS);
                case NS -> state.setValue(RAKE_DIRECTION, RakedProperty.EW);
                case SE -> state.setValue(RAKE_DIRECTION, RakedProperty.NE);
                case SW -> state.setValue(RAKE_DIRECTION, RakedProperty.SE);
                case NE -> state.setValue(RAKE_DIRECTION, RakedProperty.NW);
                case NW -> state.setValue(RAKE_DIRECTION, RakedProperty.SW);
            };
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        RakedProperty property = state.getValue(RAKE_DIRECTION);
        return switch (mirror) {
            case LEFT_RIGHT -> switch (property) {
                case EW -> state.setValue(RAKE_DIRECTION, RakedProperty.EW);
                case NS -> state.setValue(RAKE_DIRECTION, RakedProperty.NS);
                case SE -> state.setValue(RAKE_DIRECTION, RakedProperty.NE);
                case SW -> state.setValue(RAKE_DIRECTION, RakedProperty.NW);
                case NE -> state.setValue(RAKE_DIRECTION, RakedProperty.SE);
                case NW -> state.setValue(RAKE_DIRECTION, RakedProperty.SW);
            };
            case FRONT_BACK -> switch (property) {
                case EW -> state.setValue(RAKE_DIRECTION, RakedProperty.EW);
                case NS -> state.setValue(RAKE_DIRECTION, RakedProperty.NS);
                case SE -> state.setValue(RAKE_DIRECTION, RakedProperty.SW);
                case SW -> state.setValue(RAKE_DIRECTION, RakedProperty.SE);
                case NE -> state.setValue(RAKE_DIRECTION, RakedProperty.NW);
                case NW -> state.setValue(RAKE_DIRECTION, RakedProperty.NE);
            };
            default -> state;
        };
    }

    static {
        RAKE_DIRECTION = EnumProperty.create("rake_direction", RakedProperty.class);;
        SHAPE = Block.box((double)0.0F, (double)0.0F, (double)0.0F, (double)16.0F, (double)15.0F, (double)16.0F);
    }
}
