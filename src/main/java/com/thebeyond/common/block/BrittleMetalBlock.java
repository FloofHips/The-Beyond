package com.thebeyond.common.block;

import com.thebeyond.client.gui.toast.ToastManager;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrittleMetalBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final String SWORD_BASE   = "010010010";
    private static final String SHOVEL_BASE  = "010000000";
    private static final String PICKAXE_BASE = "111010010";
    private static final String AXE_BASE     = "011011010";
    private static final String HOE_BASE     = "011010010";
    private static final String AXE_BASE_M   = "110110010";
    private static final String HOE_BASE_M   = "110010010";

    private static final Map<String, String> PATTERN_TO_ITEM = new HashMap<>();

    static {
        register(SWORD_BASE, "brittle_sword");
        register(SHOVEL_BASE, "brittle_shovel");
        register(PICKAXE_BASE, "brittle_pickaxe");
        register(AXE_BASE, "brittle_axe");
        register(HOE_BASE, "brittle_hoe");
        register(AXE_BASE_M, "brittle_axe");
        register(HOE_BASE_M, "brittle_hoe");
    }

    private static void register(String base, String item) {
        for (String r : rotations(base)) {
            PATTERN_TO_ITEM.put(r, item);
        }
    }

    private static String[] rotations(String base) {
        String r0 = base;
        String r1 = rotate90(r0);
        String r2 = rotate90(r1);
        String r3 = rotate90(r2);
        return new String[]{r0, r1, r2, r3};
    }

    private static String rotate90(String s) {
        char[] out = new char[9];

        out[0] = s.charAt(6);
        out[1] = s.charAt(3);
        out[2] = s.charAt(0);
        out[3] = s.charAt(7);
        out[4] = s.charAt(4);
        out[5] = s.charAt(1);
        out[6] = s.charAt(8);
        out[7] = s.charAt(5);
        out[8] = s.charAt(2);

        return new String(out);
    }

    public static ItemStack getToolForPattern(String pattern) {
        String item = PATTERN_TO_ITEM.get(pattern);

        switch (item) {
            case "brittle_sword": return new ItemStack(BeyondItems.BRITTLE_SWORD.get());
            case "brittle_pickaxe": return new ItemStack(BeyondItems.BRITTLE_PICKAXE.get());
            case "brittle_axe": return new ItemStack(BeyondItems.BRITTLE_AXE.get());
            case "brittle_shovel": return new ItemStack(BeyondItems.BRITTLE_SHOVEL.get());
            case "brittle_hoe": return new ItemStack(BeyondItems.BRITTLE_HOE.get());
        }

        return null;
    }

    public BrittleMetalBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!(entity instanceof Player)) return;
        if (!state.getValue(POWERED)) level.scheduleTick(pos, this, 20);
        else if (level.random.nextBoolean()) level.scheduleTick(pos, this, 20);
        level.playSound(null, pos, BeyondSoundEvents.BRITTLE_METAL_IMPACT.get(), SoundSource.BLOCKS, 1, 0.8f+level.random.nextFloat());
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        level.playSound(null, pos, BeyondSoundEvents.BRITTLE_METAL_SHATTER.get(), SoundSource.BLOCKS, 1, 0.8f+level.random.nextFloat());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState()), pos.getX()+0.5f, pos.getY()+1.2f, pos.getZ()+0.5f, 10, 0.5F, 0.5F, 0.5F, 0.1F);
        BlockState blockState = !state.getValue(POWERED) ? BeyondBlocks.BRITTLE_METAL.get().defaultBlockState().setValue(POWERED, true) : BeyondBlocks.MOLTEN_METAL.get().defaultBlockState();
        level.setBlockAndUpdate(pos, blockState);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(POWERED);
    }

    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            if (!state.getValue(POWERED)) {
                level.playSound(null, pos, BeyondSoundEvents.BRITTLE_METAL_SHATTER.get(), SoundSource.BLOCKS, 1, 0.8f+level.random.nextFloat());
                level.setBlockAndUpdate(pos, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState().setValue(POWERED, true));
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        ItemStack itemStack = determineTool(level, pos);

        if (level instanceof ServerLevel serverLevel) {

            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState()), pos.getX()+0.5f, pos.getY()+1.2f, pos.getZ()+0.5f, 5, 0.5F, 0.5F, 0.5F, 0.0F);
            if (itemStack.isEmpty()) return super.useWithoutItem(state, level, pos, player, hitResult);

            level.playSound(null, pos, BeyondSoundEvents.BRITTLE_METAL_SUCCESS.get(), SoundSource.BLOCKS, 1, 1);
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5f, pos.getY() + 1, pos.getZ() + 0.5f, itemStack);
            level.addFreshEntity(entity);
            entity.setDeltaMovement(entity.getDeltaMovement().add(0,0.1,0));
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX()+0.5f, pos.getY()+1.2f, pos.getZ()+0.5f, 5, 1, 0.5F, 1, 0.01F);
        } else {
            if (player.level().isClientSide && FMLEnvironment.dist == Dist.CLIENT && itemStack.isEmpty()) {
                ToastManager.showBrittleMetalTutorialToast();
            }
            if (itemStack.isEmpty()) {
                level.playSound(player, pos, BeyondSoundEvents.BRITTLE_METAL_FAIL.get(), SoundSource.BLOCKS, 1, 0.8f + level.random.nextFloat());
                return super.useWithoutItem(state, level, pos, player, hitResult);
            }
        }
        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() > 0.2f) return;
        level.playSound(null, pos, BeyondSoundEvents.BRITTLE_METAL_REFORM.get(), SoundSource.BLOCKS, 1, 0.8f+level.random.nextFloat());
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
        return state.getValue(POWERED) ? Block.box((double)0.0F, (double)0.0F, (double)0.0F, (double)16.0F, (double)13.0F, (double)16.0F) : Block.box((double)0.0F, (double)0.0F, (double)0.0F, (double)16.0F, (double)15.0F, (double)16.0F);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(POWERED) ? 6 : 0;
    }

    public ItemStack determineTool(BlockGetter level, BlockPos pos) {
        StringBuilder pattern = new StringBuilder();
        List<BlockPos> toBreak = new ArrayList<>();

        for (int row = -1; row <= 1; row++) {
            for (int col = -1; col <= 1; col++) {
                BlockPos checkPos = pos.offset(row, 0, col);
                boolean isMetal = level.getBlockState(checkPos).is(BeyondBlocks.BRITTLE_METAL.get());
                boolean isMoltenMetal = level.getBlockState(checkPos).is(BeyondBlocks.MOLTEN_METAL.get());

                if (!isMetal && !isMoltenMetal) return ItemStack.EMPTY;

                boolean isPowered = level.getBlockState(checkPos).getValue(POWERED);
                pattern.append(isPowered ? "0" : "1");
                if (!isPowered) toBreak.add(checkPos);
            }
        }

        String current = pattern.toString();
        ItemStack stack = getToolForPattern(current);

        if (!stack.isEmpty() && !toBreak.isEmpty()) {
            for (BlockPos position : toBreak) {
                if (level instanceof ServerLevel serverLevel) serverLevel.setBlock(position, BeyondBlocks.MOLTEN_METAL.get().defaultBlockState(),3);
            }
        }

        return stack;
    }
}
