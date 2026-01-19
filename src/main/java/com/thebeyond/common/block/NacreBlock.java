package com.thebeyond.common.block;

import com.google.common.base.Suppliers;
import com.thebeyond.common.entity.RisingBlockEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.util.VoronoiNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

public class NacreBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final Supplier<VoronoiNoise> voronoiNoise = Suppliers.memoize(() ->
        new VoronoiNoise(12345L, (short) 0)
    );
    public NacreBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.isRaining()) {
            if (random.nextFloat() < 0.3)
                spawnParticles(level, pos, random, ParticleTypes.DRIPPING_OBSIDIAN_TEAR);
        }
    }

    public void spawnParticles(Level level, BlockPos pos, RandomSource random, ParticleOptions options) {
        double d0 = (double) pos.getX() + random.nextDouble();
        double d1 = (double) pos.getY() + 1;
        double d2 = (double) pos.getZ() + random.nextDouble();
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(d0, d1, d2, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }

        double d6 = random.nextDouble() * 0.3;
        level.addParticle(options, d0, d1 + d6, d2, 0.0, 0.0, 0.0);
    }

    public void spawnParticles(Level level, BlockPos pos, RandomSource random, ParticleOptions options, int amount) {
        for (int i = 0; i < amount; i++) {
            spawnParticles(level, pos, random, options);
        }
    }
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level.isRaining() && !state.getValue(POWERED))
            trigger(level, pos, 30, level.random);
        super.stepOn(level, pos, state, entity);
    }

    private void trigger(Level level, BlockPos pos, int delay, RandomSource random) {
        spawnParticles(level, pos, random, new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.NACRE.get().defaultBlockState()), 20);

        if (level.getBlockState(pos).getValue(POWERED)) return;
        level.playSound(null, pos, SoundEvents.BRUSH_GRAVEL_COMPLETED, SoundSource.BLOCKS);
        level.setBlockAndUpdate(pos, BeyondBlocks.NACRE.get().defaultBlockState().setValue(POWERED, true));

        int cell = getVariant(pos);
        for (Direction direction : Direction.values()) {
            Block block = level.getBlockState(pos.relative(direction)).getBlock();
            if (block instanceof NacreBlock nacreBlock) {
                if (isSameVariant(cell, pos.relative(direction)))
                    nacreBlock.trigger(level, pos.relative(direction), delay, random);
            }
        }

        level.scheduleTick(pos, this, delay);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.getValue(POWERED))
            level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            RisingBlockEntity risingblockentity = RisingBlockEntity.rise(level, pos, state);
            risingblockentity.disableDrop();
        }

        super.tick(state, level, pos, random);
    }
//
    //public void trigger(Level level, BlockPos pos, int delay, RandomSource random) {
    //    spawnParticles(level, pos, random, new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.NACRE.get().defaultBlockState()), 20);
//
    //    if (level.getBlockState(pos).getValue(POWERED)) return;
    //    level.playSound(null, pos, SoundEvents.BRUSH_GRAVEL_COMPLETED, SoundSource.BLOCKS);
    //    level.setBlockAndUpdate(pos, BeyondBlocks.NACRE.get().defaultBlockState().setValue(POWERED, true));
    //    level.scheduleTick(pos, this, delay);
    //}

    private boolean isSameVariant(int parent, BlockPos pos) {
        double cellSize = 2.5;
        VoronoiNoise.CellResult cell = voronoiNoise.get().getCell(
                pos.getX(),
                pos.getZ(),
                1.0 / cellSize
        );

        return parent == (Math.abs(cell.cellId()) % 4);
    }

    private int getVariant(BlockPos pos) {
        double cellSize = 2.5;

        VoronoiNoise.CellResult cell = voronoiNoise.get().getCell(
                pos.getX(),
                pos.getZ(),
                1.0 / cellSize
        );

        return (int)(Math.abs(cell.cellId()) % 4);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return !state.getValue(POWERED) ? Shapes.block() : Block.box((double)0.0F, (double)0.0F, (double)0.0F, (double)16.0F, (double)15.0F, (double)16.0F);
    }
}
