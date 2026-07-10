package com.thebeyond.common.block;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BrittleMetalBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    String SWORD =          "XIX" +
                            "XIX" +
                            "XSX";

    String SHOVEL =         "XIX" +
                            "XSX" +
                            "XSX";

    String AXE =            "IIX" +
                            "ISX" +
                            "XSX";

    String AXE_FLIPPED =    "XII" +
                            "XSI" +
                            "XSX";

    String PICKAXE =        "III" +
                            "XSX" +
                            "XSX";

    String HOE =            "IIX" +
                            "XSX" +
                            "XSX";

    String HOE_FLIPPED =    "XII" +
                            "XSX" +
                            "XSX";

    public BrittleMetalBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(POWERED) || !(entity instanceof Player)) return;
        level.scheduleTick(pos, this, 20);
        level.playSound(null, pos, SoundEvents.COPPER_BULB_BREAK, SoundSource.BLOCKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        level.playSound(null, pos, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.BLOCKS);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState()), pos.getX()+0.5f, pos.getY()+1.2f, pos.getZ()+0.5f, 10, 0.5F, 0.5F, 0.5F, 0.1F);
        level.setBlockAndUpdate(pos, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState().setValue(POWERED, true));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(POWERED);
    }

    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            if (!state.getValue(POWERED)) {
                level.playSound(null, pos, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.BLOCKS);
                level.setBlockAndUpdate(pos, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState().setValue(POWERED, true));
            }
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() > 0.2f) return;
        level.playSound(null, pos, SoundEvents.COPPER_GRATE_HIT, SoundSource.BLOCKS);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState()), pos.getX()+0.5f, pos.getY()+1.2f, pos.getZ()+0.5f, 5, 0.5F, 0.5F, 0.5F, 0.0F);
        level.setBlockAndUpdate(pos, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState().setValue(POWERED, false));
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
        return state.getValue(POWERED) ? Block.box((double)0.0F, (double)0.0F, (double)0.0F, (double)16.0F, (double)14.0F, (double)16.0F) : Block.box((double)0.0F, (double)0.0F, (double)0.0F, (double)16.0F, (double)15.0F, (double)16.0F);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(POWERED) ? 6 : 0;
    }
}
