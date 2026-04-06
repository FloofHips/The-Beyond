package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.block.EnadrakeHutBlock;
import com.thebeyond.common.block.blockstates.HutHeightProperty;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EnadrakeHutBlockEntity extends BlockEntity implements ContainerSingleItem.BlockContainerSingleItem {

    private static final int MAX_HUT_HEIGHT = 4;
    private static final double EXPANSION_CHANCE = 0.001;
    private static final int RESERVATION_TIMEOUT = 100; // ~10 seconds
    private ItemStack item;
    //private final List<CompoundTag> storedEnadrakes = new ArrayList<>();
    //private final java.util.Map<UUID, Integer> reservations = new java.util.HashMap<>();
    private CompoundTag storedEnadrake = new CompoundTag();
    private boolean pregnant = false;

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

        if (tag.contains("Occupant")) {
            CompoundTag occupant = tag.getCompound("Occupant");
            this.storedEnadrake = occupant;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.item != null && !this.item.isEmpty())
            tag.put("item", this.item.save(registries));

        if (!this.storedEnadrake.isEmpty()) {
            tag.put("Occupant", this.storedEnadrake);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (this.item != null && !this.item.isEmpty())
            tag.put("item", this.item.save(registries));

        //if (!this.storedEnadrakes.isEmpty()) {
        //    tag.putInt("OccupantCount", this.storedEnadrakes.size());
        //}
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void setPregnant() {
        this.pregnant = true;
    };

    public static void tick(Level level, BlockPos pos, BlockState state, EnadrakeHutBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (be.pregnant) {
            EnadrakeEntity entity = BeyondEntityTypes.ENADRAKE.get().create(level);
            level.addFreshEntity(entity);
            be.tryToEnter(entity);

            be.pregnant = false;
        }

        // Expire stale reservations (per-enadrake timers)
        //if (!be.reservations.isEmpty()) {
        //    be.reservations.replaceAll((uuid, timer) -> timer - 1);
        //    be.reservations.values().removeIf(timer -> timer <= 0);
        //}

        // Validate capacity: if stack shrunk, exit excess enadrakes
        //be.validateCapacity();

        // Rain exit: all enadrakes leave when it rains to harvest
        if (!be.storedEnadrake.isEmpty() && level.isRaining() && level.random.nextFloat() < 0.7) {
            be.tryToExit(false);
        }

        // POD BEHAVIOR: implemented in EnadrakeEntity (fleeToHut flag + scream signal + EnadrakeEnterHutGoal)
        // Uncomment the commented sections in EnadrakeEntity.java to enable

        boolean hasItem = (be.item != null && !be.getItem(0).isEmpty());
        if (!hasItem) {
            return;
        }

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

    /**
     * Counts how many ENADRAKE_HUT blocks are stacked above this one (including this one).
     */
    public int getStackHeight() {
        if (level == null) return 1;
        int height = 1;
        BlockPos checkPos = worldPosition;
        while (level.getBlockState(checkPos.above()).getBlock() instanceof EnadrakeHutBlock) {
            checkPos = checkPos.above();
            height++;
        }
        return height;
    }

    //public int getOccupantCount() {
    //    return this.storedEnadrakes.size();
    //}

    public boolean hasEnadrake() {
        return !this.storedEnadrake.isEmpty();
    }

    public boolean isAvailable() {
        return this.storedEnadrake.isEmpty();
    }

    /**
     * Atomically checks availability and reserves in one call.
     * Returns true if reservation succeeded.
     */
    //public boolean reserve(UUID enadrakeUUID) {
    //    if (this.reservations.containsKey(enadrakeUUID)) return true;
    //    if (!isAvailable()) return false;
    //    this.reservations.put(enadrakeUUID, RESERVATION_TIMEOUT);
    //    return true;
    //}
//
    //public void clearReservation(UUID enadrakeUUID) {
    //    this.reservations.remove(enadrakeUUID);
    //}

    /**
     * Transfers stored enadrakes to another hut block entity.
     * Exits one enadrake to account for the lost block, then migrates the rest.
     */
    //public void migrateOccupantsTo(EnadrakeHutBlockEntity target) {
    //    // Exit one enadrake (the broken block reduces capacity by 1)
    //    if (!this.storedEnadrakes.isEmpty()) {
    //        this.tryToExit(false);
    //    }
//
    //    // Move remaining enadrakes to the new base
    //    for (CompoundTag nbt : this.storedEnadrakes) {
    //        target.storedEnadrakes.add(nbt);
    //    }
    //    this.storedEnadrakes.clear();
//
    //    target.setChanged();
    //    if (target.level != null) {
    //        target.level.sendBlockUpdated(target.worldPosition, target.getBlockState(), target.getBlockState(), 3);
    //    }
    //}

    /**
     * If stack shrunk (blocks broken), exit excess enadrakes.
     */
    //public void validateCapacity() {
    //    int capacity = getStackHeight();
    //    while (this.storedEnadrakes.size() > capacity) {
    //        tryToExit(false);
    //    }
    //}

    public boolean tryToEnter(EnadrakeEntity enadrake) {
        if (this.level == null || this.level.isClientSide) return false;

        if (!this.isAvailable()) return false;

        CompoundTag nbt = new CompoundTag();
        enadrake.save(nbt);
        this.storedEnadrake = nbt;

        enadrake.setInsideHut(true);
        enadrake.setHutPosition(this.worldPosition);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.getBlockPos(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F);
            serverLevel.sendParticles(ParticleTypes.PORTAL, enadrake.position().x, enadrake.position().y+0.6, enadrake.position().z, 20, 0.3, 0.3, 0.3, 0.05);
        }

        enadrake.remove(Entity.RemovalReason.DISCARDED);

        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);

        return true;
    }

    /**
     * Exits one enadrake (first in list). Called when a hut block is broken.
     */
    public void tryToExit(boolean angerOne) {
        if (this.level == null || this.level.isClientSide) return;
        if (this.storedEnadrake.isEmpty()) return;
        if (!(this.level instanceof ServerLevel serverLevel)) return;

        CompoundTag nbt = this.storedEnadrake;
        this.storedEnadrake = new CompoundTag();
        BlockPos exitPos = this.findExitPosition();

        serverLevel.playSound(null, this.getBlockPos(), SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F);

        Entity entity = EntityType.loadEntityRecursive(nbt, serverLevel, (e) -> {
            e.moveTo(exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5, e.getYRot(), e.getXRot());
            return e;
        });

        if (entity instanceof EnadrakeEntity enadrake) {
            enadrake.setInsideHut(false);
            enadrake.setHutPosition(null);
            serverLevel.addFreshEntity(enadrake);

            serverLevel.sendParticles(ParticleTypes.PORTAL, enadrake.position().x, enadrake.position().y+0.6, enadrake.position().z, 20, 0.3, 0.3, 0.3, 0.05);

            if (angerOne)
                enadrake.panic = 220;
        }

        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    /**
     * Exits all enadrakes. Called when it starts raining or base is broken.
     */
    //public void exitAll(boolean angerOne) {
    //    if (this.level == null || this.level.isClientSide) return;
    //    if (!(this.level instanceof ServerLevel serverLevel)) return;
//
    //    EnadrakeEntity angryEnadrake = null;
//
    //    List<BlockPos> usedPositions = new ArrayList<>();
    //    while (!this.storedEnadrakes.isEmpty()) {
    //        CompoundTag nbt = this.storedEnadrakes.remove(0);
    //        BlockPos exitPos = this.findExitPosition(usedPositions);
    //        usedPositions.add(exitPos);
//
    //        Entity entity = EntityType.loadEntityRecursive(nbt, serverLevel, (e) -> {
    //            e.moveTo(exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5, e.getYRot(), e.getXRot());
    //            return e;
    //        });
//
    //        if (entity instanceof EnadrakeEntity enadrake) {
    //            enadrake.setInsideHut(false);
    //            enadrake.setHutPosition(null);
    //            serverLevel.addFreshEntity(enadrake);
    //            if (angryEnadrake == null)
    //                angryEnadrake = enadrake;
    //        }
    //    }
//
    //    if (angerOne) {
    //        if (angryEnadrake!=null)
    //            angryEnadrake.panic = 220;
    //    }
//
    //    this.setChanged();
    //    this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    //}

    private BlockPos findExitPosition() {
        return findExitPosition(List.of());
    }

    private BlockPos findExitPosition(List<BlockPos> usedPositions) {
        BlockPos pos = this.worldPosition;

        // Try facing direction first
        Direction facing = this.getBlockState().getValue(EnadrakeHutBlock.FACING);
        BlockPos checkPos = pos.relative(facing);
        if (isExitValid(checkPos) && !usedPositions.contains(checkPos)) {
            return checkPos;
        }

        // Try all horizontal sides
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            checkPos = pos.relative(dir);
            if (isExitValid(checkPos) && !usedPositions.contains(checkPos)) {
                return checkPos;
            }
        }

        // Try above in expanding radius
        for (int dy = 1; dy <= 3; dy++) {
            checkPos = pos.above(dy);
            if (isExitValid(checkPos) && !usedPositions.contains(checkPos)) {
                return checkPos;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                checkPos = pos.relative(dir).above(dy);
                if (isExitValid(checkPos) && !usedPositions.contains(checkPos)) {
                    return checkPos;
                }
            }
        }

        // Last resort: above the hut (even if used by another)
        return pos.above();
    }

    private boolean isExitValid(BlockPos exitPos) {
        return this.level.getBlockState(exitPos).getCollisionShape(this.level, exitPos).isEmpty();
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
                for (int y = 10; y >= -10; y--) {
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

        float hutChance = 0.7f;

        if (rarity == Rarity.UNCOMMON) {
            hutChance = 0.9F;
        }

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

            placeBlock(level, facing, newPos);

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
