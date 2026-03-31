package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.block.EnadrakeHutBlock;
import com.thebeyond.common.block.blockstates.HutHeightProperty;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EnadrakeHutBlockEntity extends BlockEntity implements ContainerSingleItem.BlockContainerSingleItem {

    private static final int MAX_HUT_HEIGHT = 4;
    private ItemStack item;
    private static final double EXPANSION_CHANCE = 0.001;

    public EnadrakeHutBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        if (item == null) item = ItemStack.EMPTY;
    }
    public EnadrakeHutBlockEntity(BlockPos pos, BlockState blockState) {
        this(BeyondBlockEntities.ENADRAKE_HUT.get(), pos, blockState);
        if (item == null) item = ItemStack.EMPTY;
    }
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("item", 10)) {
            this.item = (ItemStack)ItemStack.parse(registries, tag.getCompound("item")).orElse(ItemStack.EMPTY);
        } else {
            this.item = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.item != null && !this.item.isEmpty())
            tag.put("item", this.item.save(registries));

    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (this.item != null && !this.item.isEmpty())
            tag.put("item", this.item.save(registries));

        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EnadrakeHutBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean hasItem = (be.item != null && !be.getItem(0).isEmpty());

        if (!hasItem) {
            return;
        }

        if (hasItem) {
            if (level.random.nextDouble() < 0.05) {
                if (be.canExpandHut()) {
                    be.tryExpandHut(serverLevel);
                    ItemStack stack = be.getItem(0);
                    stack.shrink(1);
                    be.setItem(0, stack);
                    be.setChanged();
                }
            }
        }
    }

    public boolean tryToEnter(EnadrakeEntity enadrake) {
        //TODO

        return false;
    }

    public void tryToExit() {
        //TODO
    }

    private boolean canEnadrakeEnter() {
        //TODO
        return true;
    }

    private BlockPos findExitPosition() {
        BlockPos pos = this.worldPosition;

        Direction facing = this.getBlockState().getValue(EnadrakeHutBlock.FACING);
        BlockPos checkPos = pos.relative(facing);

        if (this.level.getBlockState(checkPos).isAir()) {
            return checkPos;
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            checkPos = pos.relative(dir);
            if (this.level.getBlockState(checkPos).isAir() &&
                    this.level.getBlockState(checkPos.above()).isAir()) {
                return checkPos;
            }
        }

        return pos.above();
    }

    private boolean canExpandHut() {
        if (level == null) return false;
        if (getItem(0).isEmpty()) return false;
        if (canExpandUpward()) {
            return true;
        }

        return canExpandNearby();
    }

    private boolean canExpandUpward() {
        if (level == null) return false;

        BlockPos currentPos = worldPosition;
        int currentHeight = 1;

        while (level.getBlockState(currentPos.above()).getBlock() instanceof EnadrakeHutBlock) {
            currentPos = currentPos.above();
            currentHeight++;
        }

        if (currentHeight < 4) {
            BlockPos abovePos = currentPos.above();
            return level.getBlockState(abovePos).isAir();
        }

        return false;
    }

    private boolean canExpandNearby() {
        if (level == null) return false;

        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = -10; y <= 10; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    if (x % 2 != 0 || z % 2 != 0) continue;

                    BlockPos checkPos = worldPosition.offset(x, y, z);

                    if (isValidHutPosition(checkPos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isValidHutPosition(BlockPos pos) {
        if (level == null) return false;

        if (!level.getBlockState(pos).canBeReplaced()) return false;

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (!level.getBlockState(pos.above()).isAir()) return false;
        if (level.getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock) return true;
        if (belowState.isAir() || belowState.liquid() || !belowState.isFaceSturdy(level, belowPos, Direction.UP)) return false;


        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                BlockPos neighborPos = pos.offset(x, 0, z);
                if (level.getBlockState(neighborPos).getBlock() instanceof EnadrakeHutBlock) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean tryExpandHut(ServerLevel level) {
        if (canExpandUpward()) {
            level.playSound(null, this.getBlockPos(), SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F);
            return expandUpward(level);
        }

        return expandNearby(level);
    }

    private boolean expandUpward(ServerLevel level) {
        BlockPos currentPos = worldPosition;

        while (!level.getBlockState(currentPos.above()).isAir()) {
            currentPos = currentPos.above();
        }

        BlockPos newPos = currentPos.above();
        Direction facing = getBlockState().getValue(EnadrakeHutBlock.FACING);


        if (level.random.nextDouble() < 0.05) {
            createPlatformAndHouse(level, newPos, facing);
        } else {
            placeBlock(level, facing, newPos);
        }

        return true;
    }

    private void placeBlock(ServerLevel level, Direction facing, BlockPos newPos) {
        BlockState newState = getBlockState(facing);
        boolean placeDefault = true;
        if (newState == BeyondBlocks.ENGRAVED_END_STONE.get().defaultBlockState() && level.random.nextFloat() < 0.2) {
            for (Direction dir : Direction.values()) {
                if (dir.getAxis().isVertical()) continue;
                BlockPos pos = newPos.below().offset(dir.getStepX(), 0, dir.getStepZ());
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, dir), Block.UPDATE_ALL);
                    placeDefault = false;
                }
            }
            if (placeDefault) level.setBlock(newPos, Blocks.END_ROD.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }

        level.setBlock(newPos, newState, Block.UPDATE_ALL);
    }

    private @NotNull BlockState getBlockState(Direction facing) {
        Rarity rarity = item.getRarity();

        if (rarity == Rarity.EPIC)
            return BeyondBlocks.ENADRAKE_FLARE.get().defaultBlockState();

        float hutChance = 0.5f;
        if (rarity == Rarity.UNCOMMON) hutChance = 0.8F;
        if (rarity == Rarity.RARE) {
            hutChance = 0.7F;
            return level.random.nextFloat() < hutChance ? BeyondBlocks.ENADRAKE_HUT.get().defaultBlockState()
                    .setValue(EnadrakeHutBlock.FACING, facing) : BeyondBlocks.ENADRAKE_FLARE.get().defaultBlockState();
        }

        return level.random.nextFloat() < hutChance ? BeyondBlocks.ENADRAKE_HUT.get().defaultBlockState()
                    .setValue(EnadrakeHutBlock.FACING, facing) : BeyondBlocks.ENGRAVED_END_STONE.get().defaultBlockState();
    }

    private void createPlatformAndHouse(ServerLevel level, BlockPos hutPos, Direction facing) {
        if (!hasSpaceForPlatform(level, hutPos)) return;
        if(!level.getBlockState(hutPos).isAir()) return;

        level.setBlock(hutPos, BeyondBlocks.ENGRAVED_END_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
        BlockPos s1 = hutPos.offset(facing.getStepX(), 0, facing.getStepZ());
        BlockPos s2 = hutPos.offset(-facing.getStepX(), 0, -facing.getStepZ());

        placeEndStoneStair(level, s1, facing);
        placeEndStoneStair(level, s2, facing.getOpposite());

        if(level.getBlockState(s1.above()).isAir()) {
            placeBlock(level, facing, s1.above());
            return;
        }

        if(level.getBlockState(s2.above()).isAir()) {
            placeBlock(level, facing, s2.above());
            return;
        }
    }

    private boolean hasSpaceForPlatform(ServerLevel level, BlockPos platformPos) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos checkPos = platformPos.offset(x, 0, z);
                if (!level.getBlockState(checkPos).isAir()) {
                    return false;
                }
            }
        }

        return true;
    }

    private void placeEndStoneStair(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState stairState = Blocks.END_STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, facing.getOpposite())
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT)
                .setValue(StairBlock.WATERLOGGED, false);

        level.setBlock(pos, stairState, Block.UPDATE_ALL);
    }

    private boolean expandNearby(ServerLevel level) {
        List<BlockPos> validPositions = new ArrayList<>();

        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = -10; y <= 10; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockPos checkPos = worldPosition.offset(x, y, z);
                    if (isValidHutPosition(checkPos)) {
                        validPositions.add(checkPos);
                    }
                }
            }
        }

        if (!validPositions.isEmpty()) {
            BlockPos newPos = validPositions.get(level.random.nextInt(validPositions.size()));
            Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.random);

            placeBlock(level, facing, newPos);

            if (!(level.getBlockState(newPos.below()).is(BeyondBlocks.ENGRAVED_END_STONE.get())) && !(level.getBlockState(newPos.below()).is(Blocks.END_STONE_BRICK_STAIRS))){
                if (level.getBlockState(newPos.below()).is(BeyondTags.END_DECORATOR_REPLACEABLE) || level.getBlockState(newPos.below()).is(BlockTags.MOSS_REPLACEABLE))
                    level.setBlock(newPos.below(), BeyondBlocks.ZYMOTE.get().defaultBlockState(), Block.UPDATE_ALL);

                for (Direction d : Direction.values()) {
                    if (d.getAxis().isVertical()) continue;
                    BlockPos p = newPos.below().offset(d.getStepX(), 0, d.getStepZ());
                    if (!level.getBlockState(p).is(Blocks.END_ROD) && (level.getBlockState(p).is(BeyondTags.END_DECORATOR_REPLACEABLE) || level.getBlockState(p).is(BlockTags.MOSS_REPLACEABLE)))
                        level.setBlock(p, BeyondBlocks.ZYMOTE.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;
                    BlockPos checkPos = newPos.offset(x, 0, z);

                    if (!level.getBlockState(checkPos.below()).is(BeyondBlocks.ZYMOTE) && level.getBlockState(checkPos).canBeReplaced() && level.getBlockState(checkPos.below()).isSolid()) {
                        level.setBlock(checkPos, BeyondBlocks.CREEPING_ZYMOTE.get().defaultBlockState()
                                        .setValue(MultifaceBlock.getFaceProperty(Direction.UP), false)
                                        .setValue(MultifaceBlock.getFaceProperty(Direction.EAST), false)
                                        .setValue(MultifaceBlock.getFaceProperty(Direction.WEST), false)
                                        .setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), false)
                                        .setValue(MultifaceBlock.getFaceProperty(Direction.SOUTH), false)
                                        .setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
                                , Block.UPDATE_ALL);
                    }
                }
            }

            level.playSound(null, this.getBlockPos(), SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F);
            return true;
        }

        return false;
    }


    @Override
    public void setChanged() {
        super.setChanged();
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    @Override
    public boolean stillValid(Player player) {
        return BlockContainerSingleItem.super.stillValid(player);
    }

    @Override
    public ItemStack getTheItem() {
        return this.item;
    }

    @Override
    public void setTheItem(ItemStack itemStack) {
        this.item = itemStack;
    }

    @Override
    public ItemStack removeTheItem() {
        return BlockContainerSingleItem.super.removeTheItem();
    }
}
