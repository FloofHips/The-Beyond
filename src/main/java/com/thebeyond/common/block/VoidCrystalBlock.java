package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockstates.PillarHeightProperty;
import com.thebeyond.common.entity.RisingBlockEntity;
import com.thebeyond.common.fluid.GellidVoidBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public class VoidCrystalBlock extends Block implements Fallable {
    public static final MapCodec<VoidCrystalBlock> CODEC = simpleCodec(VoidCrystalBlock::new);
    public static final EnumProperty<PillarHeightProperty> HEIGHT;
    public static final BooleanProperty UP;
    public VoidCrystalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(UP, Boolean.TRUE)).setValue(HEIGHT, PillarHeightProperty.TIP)));
    }
    public MapCodec<VoidCrystalBlock> codec() {
        return CODEC;
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{UP, HEIGHT});
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 vec3 = state.getOffset(level, pos);

        if(state.getValue(HEIGHT) == PillarHeightProperty.TIP) {
            if(state.getValue(UP))
                return TIP_SHAPE.move(vec3.x, 0.0, vec3.z);
            return TIP_SHAPE_DOWN.move(vec3.x, 0.0, vec3.z);
        }
        return CORE_SHAPE.move(vec3.x, 0.0, vec3.z);
    }

    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {

        if (state.getValue(UP)) return !level.getBlockState(pos.below()).isAir();
        if (!state.getValue(UP)) return !level.getBlockState(pos.above()).isAir();

        return false;
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction clickedFace = context.getClickedFace();
        BlockPos pos = context.getClickedPos();

        if ((context.getLevel().getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock || context.getLevel().getBlockState(pos.below()).getBlock() instanceof GellidVoidBlock || canSupportCenter(level, pos.below(), Direction.UP)) && clickedFace != Direction.DOWN) {
            return this.defaultBlockState().setValue(UP, true).setValue(HEIGHT, PillarHeightProperty.TIP);
        }

        return this.defaultBlockState()
                .setValue(UP, false)
                .setValue(HEIGHT, PillarHeightProperty.TIP);

    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) level.scheduleTick(pos, this, 2);

        if (state.getValue(UP)) {
            if (level.getBlockState(pos.below()).isAir()){
                return state;
            }
           if (!(level.getBlockState(pos.above()).getBlock() instanceof VoidCrystalBlock)){
              return this.defaultBlockState().setValue(UP, true).setValue(HEIGHT, PillarHeightProperty.TIP);
           }
           else {
               if (level.getBlockState(pos.above()).getBlock() instanceof VoidCrystalBlock && level.getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock) {
                   return this.defaultBlockState().setValue(UP, true).setValue(HEIGHT, PillarHeightProperty.CORE);
               }
               if (!(level.getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock)) {
                   return this.defaultBlockState().setValue(UP, true).setValue(HEIGHT, PillarHeightProperty.BASE);
               }
           }
        }

        if (!state.getValue(UP)) {
            if (level.getBlockState(pos.above()).isAir()){
                return state;
            }
            if (!(level.getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock)){
                return this.defaultBlockState().setValue(UP, false).setValue(HEIGHT, PillarHeightProperty.TIP);
            }
            else {
                if (level.getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock && level.getBlockState(pos.above()).getBlock() instanceof VoidCrystalBlock) {
                    return this.defaultBlockState().setValue(UP, false).setValue(HEIGHT, PillarHeightProperty.CORE);
                }
                if (!(level.getBlockState(pos.above()).getBlock() instanceof VoidCrystalBlock)) {
                    return this.defaultBlockState().setValue(UP, false).setValue(HEIGHT, PillarHeightProperty.BASE);
                }
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockpos = hit.getBlockPos();
        if (!level.isClientSide && projectile.mayInteract(level, blockpos)) {
            level.destroyBlock(blockpos, true, projectile);
        }
        super.onProjectileHit(level, state, hit, projectile);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(UP)) {
            spawnRisingStalactite(state, level, pos);
        } else {
            spawnFallingStalactite(state, level, pos);
        }
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        Predicate<Entity> predicate = EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE);
        AABB bb = new AABB(pos).inflate(1.5);

        level.getEntities(fallingBlock, bb, predicate).forEach((entity) -> {
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.addEffect(new MobEffectInstance(BeyondEffects.UNSTABLE, 400));
                livingEntity.addEffect(new MobEffectInstance(BeyondEffects.WEIGHTLESS, 400,1));
            }
        });

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(fallingBlock, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1, 1);
            serverLevel.sendParticles(ColorUtils.voidOptions, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.25, 0.15, 0.25, 0);
        }
        Fallable.super.onBrokenAfterFall(level, pos, fallingBlock);
    }

    private static void spawnRisingStalactite(BlockState state, ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        for(BlockState blockstate = state; isStalagmite(blockstate); blockstate = level.getBlockState(blockpos$mutableblockpos)) {
            RisingBlockEntity risingblockentity = RisingBlockEntity.rise(level, blockpos$mutableblockpos, blockstate);
            if (isTip(blockstate)) {
                BlockPos.MutableBlockPos checkPos = blockpos$mutableblockpos.mutable().move(Direction.DOWN);
                while (isStalagmite(level.getBlockState(checkPos))) {
                    checkPos.move(Direction.DOWN);
                }
                risingblockentity.setHurtsEntities(0.1f, 4);
                break;
            }

            blockpos$mutableblockpos.move(Direction.UP);
        }
    }
    private static void spawnFallingStalactite(BlockState state, ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        for(BlockState blockstate = state; isStalactite(blockstate); blockstate = level.getBlockState(blockpos$mutableblockpos)) {
            FallingBlockEntity fallingblockentity = FallingBlockEntity.fall(level, blockpos$mutableblockpos, blockstate);
            if (isTip(blockstate)) {
                fallingblockentity.setHurtsEntities(0.1f, 4);
                break;
            }

            blockpos$mutableblockpos.move(Direction.DOWN);
        }

    }

    private static boolean isStalactite(BlockState state) {
        return isPointedDripstoneWithDirection(state, false);
    }

    private static boolean isStalagmite(BlockState state) {
        return isPointedDripstoneWithDirection(state, true);
    }
    private static boolean isPointedDripstoneWithDirection(BlockState state, Boolean up) {
        return state.is(BeyondBlocks.VOID_CRYSTAL.get()) && state.getValue(UP) == up;
    }

    private static boolean isTip(BlockState state) {
        if (!state.is(BeyondBlocks.VOID_CRYSTAL.get())) {
            return false;
        } else {
            PillarHeightProperty height = state.getValue(HEIGHT);
            return height == PillarHeightProperty.TIP;
        }
    }

    private static final VoxelShape TIP_SHAPE;
    private static final VoxelShape TIP_SHAPE_DOWN;
    private static final VoxelShape CORE_SHAPE;

    static {
        UP = BlockStateProperties.UP;
        HEIGHT = EnumProperty.create("height", PillarHeightProperty.class);

        TIP_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0);
        TIP_SHAPE_DOWN = Block.box(3.0, 3.0, 3.0, 13.0, 16.0, 13.0);
        CORE_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    }
}
