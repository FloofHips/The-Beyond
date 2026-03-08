package com.thebeyond.common.block.blockentities;

import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.thebeyond.common.block.RefugeBlock;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.util.RefugeChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class RefugeBlockEntity extends BlockEntity implements MenuProvider {
    @Nullable
    private Component name;
    public static final Map<ServerLevel, Set<RefugeBlockEntity>> ACTIVE_REFUGES = new HashMap<>();
    //private UUID owner;
    @Nullable
    private ResolvableProfile owner;
    private int tickCounter;
    public boolean isActive;
    private final Set<ChunkPos> affectedChunks = new HashSet<>();
    private ContainerData dataAccess;

    private static final Component DEFAULT_NAME = Component.translatable("container.refuge");
    private static final byte MODE_HUNGER = 0;
    private static final byte MODE_EXPLOSION = 1;
    private static final byte MODE_MOB_SPAWN = 2;
    private static final byte MODE_FALL_DAMAGE = 3;
    private static final int PROTECTION_RADIUS = 64;

    private byte currentMode = -1;;

    public byte animating = 0;

    public RefugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public RefugeBlockEntity(BlockPos pos, BlockState blockState) {
        this(BeyondBlockEntities.REFUGE.get(), pos, blockState);
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int i) {
                return 0;
            }

            @Override
            public void set(int i, int i1) {

            }

            @Override
            public int getCount() {
                return 0;
            }
        };
    }

    @Override
    public DataComponentMap components() {
        return super.components();
    }

    public void print() {
        String[][] pattern = {
                {"0", "0", "0", "0", "0", "0", "*", "*", "*", "*"},
                {"0", "0", "0", "0", "0", "0", "*", "*", "*", "*"},
                {"M", "0", "0", "0", "0", "0", "*", "*", "*", "*"},
                {"M", "*", "*", "*", "*", "*", "*", "*", "*", "*"},
                {"*", "*", "*", "*", "*", "*", "*", "*", "0", "0"},
                {"*", "*", "*", "*", "*", "*", "*", "*", "0", "0"}
        };

        Random random = new Random();

        for (int iteration = 1; iteration <= 4; iteration++) {
            List<int[]> star = new ArrayList<>();
            for (int i = 0; i < pattern.length; i++) {
                for (int j = 0; j < pattern[i].length; j++) {
                    if (pattern[i][j].equals("*")) {
                        star.add(new int[]{i, j});
                    }
                }
            }

            if (star.isEmpty()) {
                break;
            }

            int[] selected = star.get(random.nextInt(star.size()));
            int row = selected[0];
            int col = selected[1];

            for (int i = row - 1; i <= row + 1; i++) {
                for (int j = col - 1; j <= col + 1; j++) {
                    if (i >= 0 && i < pattern.length && j >= 0 && j < pattern[i].length) {
                        if (pattern[i][j] == "*")
                            pattern[i][j] = "M";
                    }
                }
            }

            pattern[row][col] = "K";
        }

        for (int col = pattern[0].length - 4; col >= 0; col--) {
            boolean hasM = false;
            List<Integer> starRows = new ArrayList<>();

            for (int row = 0; row < pattern.length; row++) {
                if (pattern[row][col].equals("M")) {
                    hasM = true;
                    break;
                }
            }

            if (!hasM) {
                for (int row = 0; row < pattern.length; row++) {
                    if (pattern[row][col].equals("*")) {
                        starRows.add(row);
                    }
                }

                if (!starRows.isEmpty()) {
                    int randomRow = starRows.get(random.nextInt(starRows.size()));
                    if (pattern[randomRow][col].equals("*"))
                        pattern[randomRow][col] = "G";
                    if (col < 9) {
                        if (pattern[randomRow][col + 1].equals("*"))
                            pattern[randomRow][col + 1] = "G";
                    }
                }
            }
        }

        printPattern(sanitizePattern(pattern));
    }

    private String[][] sanitizePattern(String[][] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            for (int j = 0; j < pattern[i].length; j++) {
                if (pattern[i][j].equals("M") || pattern[i][j].equals("G")) {
                    pattern[i][j] = "X";
                } else pattern[i][j] = "O";
            }
        }
        return pattern;
    }

    public void printPattern(String[][] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            for (int j = 0; j < pattern[i].length; j++) {
                if (pattern[i][j].equals("X")) {

                    BlockPos pos = this.worldPosition.offset(i, j, 0);
                    level.setBlock(pos, BeyondBlocks.OBIROOT_ARM.get().defaultBlockState(), 3);
                    pos = this.worldPosition.offset(-i, j, 0);
                    level.setBlock(pos, BeyondBlocks.OBIROOT_ARM.get().defaultBlockState(), 3);
                    pos = this.worldPosition.offset(0, j, i);
                    level.setBlock(pos, BeyondBlocks.OBIROOT_ARM.get().defaultBlockState(), 3);
                    pos = this.worldPosition.offset(0, j, -i);
                    level.setBlock(pos, BeyondBlocks.OBIROOT_ARM.get().defaultBlockState(), 3);
                }
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("profile")) {
            ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, tag.get("profile")).resultOrPartial(error -> System.out.println("Failed to load : " + error)).ifPresent(profile -> this.owner = profile);
        }
        if (tag.contains("CurrentMode")) {
            this.currentMode = tag.getByte("CurrentMode");
        } else {
            this.currentMode = -1;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.owner != null) {
            tag.put("profile", (Tag)ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, this.owner).getOrThrow());
        }
        if (currentMode != -1) {
            tag.putByte("CurrentMode", currentMode);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    public void setMode(byte i, RefugeBlockEntity be) {
        be.updateAllChunks(i);
    }
    public byte getMode() {
        return currentMode;
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.owner;
    }
    public void setOwner(ItemStack stack) {
        if (stack.is(Items.PLAYER_HEAD)) {
            ResolvableProfile resolvableprofile = (ResolvableProfile)stack.get(DataComponents.PROFILE);
            if (resolvableprofile != null && resolvableprofile.name().isPresent()) {
                this.owner = resolvableprofile;
            }
        }
    }


    public void setOwner(@Nullable ResolvableProfile owner) {
        synchronized(this) {
            this.owner = owner;
        }

        this.updateOwnerProfile();
    }

    private void updateOwnerProfile() {
        if (this.owner != null && !this.owner.isResolved()) {
            this.owner.resolve().thenAcceptAsync((p_332638_) -> {
                this.owner = p_332638_;
                this.setChanged();
            }, SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR);
        } else {
            this.setChanged();
        }
    }

    private void makeChunks() {
        affectedChunks.clear();
        int chunkX = worldPosition.getX() / 16;
        int chunkZ = worldPosition.getZ() / 16;
        int radiusChunks = (PROTECTION_RADIUS / 16) + 1;

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                int worldX = (chunkX + dx) * 16 + 8;
                int worldZ = (chunkZ + dz) * 16 + 8;

                if (Math.abs(worldX - worldPosition.getX()) <= PROTECTION_RADIUS && Math.abs(worldZ - worldPosition.getZ()) <= PROTECTION_RADIUS) {
                    ChunkPos chunkPos = new ChunkPos(chunkX + dx, chunkZ + dz);
                    affectedChunks.add(chunkPos);
                    level.setBlock(chunkPos.getMiddleBlockPosition(-52), Blocks.LIME_CONCRETE.defaultBlockState(), 3);
                }
            }
        }
    }

    public Set<ChunkPos> getAffectedChunks() {
        if (affectedChunks == null) makeChunks();
        return affectedChunks;
    }

    private void updateAllChunks(byte newMode) {
        if (level == null || level.isClientSide) return;
        makeChunks();

        for (ChunkPos chunkPos : affectedChunks) {
            LevelChunk chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
            if (chunk != null) {
                RefugeChunkData data = chunk.getData(BeyondAttachments.REFUGE_DATA);
                if (currentMode > -1 && currentMode < 4) {
                    data.removeRefuge(currentMode);
                }
                data.addRefuge(newMode);
            }
        }

        currentMode = newMode;
        fill();
    }

    public void remove() {
        if (level == null || level.isClientSide) return;

        for (ChunkPos chunkPos : affectedChunks) {
            LevelChunk chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
            if (chunk != null) {
                RefugeChunkData data = chunk.getData(BeyondAttachments.REFUGE_DATA);
                if (currentMode > -1 && currentMode < 4) {
                    data.removeRefuge(currentMode);
                }
                level.setBlock(chunkPos.getMiddleBlockPosition(-48), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                level.setBlock(chunkPos.getMiddleBlockPosition(-49), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                level.setBlock(chunkPos.getMiddleBlockPosition(-50), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                level.setBlock(chunkPos.getMiddleBlockPosition(-51), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                level.setBlock(chunkPos.getMiddleBlockPosition(-52), Blocks.RED_CONCRETE.defaultBlockState(), 3);
            }
        }

    }

    private void fill() {
        if (level == null || level.isClientSide) return;

        for (ChunkPos chunkPos : affectedChunks) {
            LevelChunk chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
            if (chunk != null) {
                RefugeChunkData data = chunk.getData(BeyondAttachments.REFUGE_DATA);
                //if (isActive) {

                if (data.shouldPreventHunger())
                    level.setBlock(chunkPos.getMiddleBlockPosition(-50), Blocks.BROWN_CONCRETE.defaultBlockState(), 3);
                if (data.shouldPreventExplosion())
                    level.setBlock(chunkPos.getMiddleBlockPosition(-49), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                if (data.shouldPreventMobSpawn())
                    level.setBlock(chunkPos.getMiddleBlockPosition(-48), Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
                if (data.shouldPreventFallDamage())
                    level.setBlock(chunkPos.getMiddleBlockPosition(-51), Blocks.BLUE_CONCRETE.defaultBlockState(), 3);


                //}else {
                if (!data.shouldPreventHunger())
                    level.setBlock(chunkPos.getMiddleBlockPosition(-50), Blocks.GLASS.defaultBlockState(), 3);
                if (!data.shouldPreventExplosion())
                    level.setBlock(chunkPos.getMiddleBlockPosition(-49), Blocks.GLASS.defaultBlockState(), 3);
                if (!data.shouldPreventMobSpawn())
                    level.setBlock(chunkPos.getMiddleBlockPosition(-48), Blocks.GLASS.defaultBlockState(), 3);
                if (!data.shouldPreventFallDamage())
                    level.setBlock(chunkPos.getMiddleBlockPosition(-51), Blocks.GLASS.defaultBlockState(), 3);
                //}
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RefugeBlockEntity be) {
        if (level.isClientSide) return;

        be.tickCounter++;

        if (be.animating > 0)
            be.animating--;

        boolean shouldBeActive = RefugeBlock.isActive(state);
        if (shouldBeActive != be.isActive) {
            be.isActive = shouldBeActive;
        //    be.updateAllChunks();
            if (shouldBeActive) {
                registerRefuge((ServerLevel) level, be);
            } else {
                unregisterRefuge((ServerLevel) level, be);
            }
        }
    }

    private static void registerRefuge(ServerLevel level, RefugeBlockEntity be) {
        ACTIVE_REFUGES.computeIfAbsent(level, k -> new HashSet<>()).add(be);
    }

    private static void unregisterRefuge(ServerLevel level, RefugeBlockEntity be) {
        Set<RefugeBlockEntity> refuges = ACTIVE_REFUGES.get(level);
        if (refuges != null) {
            refuges.remove(be);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            unregisterRefuge(serverLevel, this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && isActive) {
            registerRefuge(serverLevel, this);
        }
    }

    public void activate(Player player) {
        if (!level.isClientSide) {

            setChanged();
        }
    }

    public Component getDisplayName() {
        return this.getName();
    }

    public Component getName() {
        return this.name != null ? this.name : DEFAULT_NAME;
    }

    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        this.name = componentInput.get(DataComponents.CUSTOM_NAME);
        this.owner = componentInput.get(DataComponents.PROFILE);
    }

    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.name);
        components.set(DataComponents.PROFILE, this.owner);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RefugeMenu(containerId, inventory, this.dataAccess, ContainerLevelAccess.create(this.level, this.getBlockPos()));
    }
}
