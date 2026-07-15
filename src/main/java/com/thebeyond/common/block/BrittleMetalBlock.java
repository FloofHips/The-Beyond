package com.thebeyond.common.block;

import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BrittleMetalBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    final String  sword = "010010010";
    final String  shovel = "010000000";
    final String  pickaxe = "111010010";

    final String  axe = "011011010";
    final String  axe_2 = "110110010";

    final String  hoe = "011010010";
    final String  hoe_2 = "110010010";

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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        ItemStack itemStack = determineTool(level, pos, Direction.fromYRot(player.getNearestViewDirection().toYRot()));
        level.playSound(null, pos, SoundEvents.COPPER_BULB_BREAK, SoundSource.BLOCKS);

        if (level instanceof ServerLevel serverLevel) {

            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState()), pos.getX()+0.5f, pos.getY()+1.2f, pos.getZ()+0.5f, 5, 0.5F, 0.5F, 0.5F, 0.0F);
            if (itemStack.isEmpty()) return super.useWithoutItem(state, level, pos, player, hitResult);

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.destroyBlock(pos.offset(x, 0, z), false);
                }
            }

            ItemEntity entity = new ItemEntity(level, pos.getX(), pos.getY() + 1, pos.getZ(), itemStack);
            level.addFreshEntity(entity);
            entity.setDeltaMovement(entity.getDeltaMovement().add(0,0.1,0));

            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX()+0.5f, pos.getY()+1.2f, pos.getZ()+0.5f, 5, 1, 0.5F, 1, 0.01F);
        }
        return InteractionResult.SUCCESS_NO_ITEM_USED;
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

    public ItemStack determineTool(BlockGetter level, BlockPos pos, Direction dir) {
        StringBuilder pattern = new StringBuilder();

        Direction left = dir.getClockWise();
        Direction forward = dir;

        for (int row = -1; row <= 1; row++) {
            for (int col = -1; col <= 1; col++) {
                BlockPos checkPos = pos.relative(forward, -row).relative(left, col);
                boolean isMetal = level.getBlockState(checkPos).is(BeyondBlocks.BRITTLE_METAL.get());
                if (!isMetal) return ItemStack.EMPTY;

                boolean isPowered = level.getBlockState(checkPos).getValue(POWERED);
                pattern.append(isPowered ? "0" : "1");
            }
        }

        String current = pattern.toString();

        switch (current) {
            case pickaxe: return new ItemStack(BeyondItems.BRITTLE_PICKAXE.get());
            case axe, axe_2: return new ItemStack(BeyondItems.BRITTLE_AXE.get());
            case hoe, hoe_2: return new ItemStack(BeyondItems.BRITTLE_HOE.get());
            case shovel: return new ItemStack(BeyondItems.BRITTLE_SHOVEL.get());
            case sword: return new ItemStack(BeyondItems.BRITTLE_SWORD.get());
        }

        return ItemStack.EMPTY;
    }
}
