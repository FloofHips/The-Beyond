package com.thebeyond.common.block.blockentities;

import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.thebeyond.common.block.RefugeBlock;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.util.ColorUtils;
import com.thebeyond.util.RefugeChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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
    public static final byte MODE_HUNGER = 0;
    public static final byte MODE_EXPLOSION = 1;
    public static final byte MODE_MOB_SPAWN = 2;
    public static final byte MODE_FALL_DAMAGE = 3;
    private static final int PROTECTION_RADIUS = 9;
    public float oRot = 0;
    public float tRot = 0;
    public float rot = 0;

    public byte currentMode = -1;
    public byte animating = 0;
    public String[][] pattern;

    public RefugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public RefugeBlockEntity(BlockPos pos, BlockState blockState) {
        this(BeyondBlockEntities.REFUGE.get(), pos, blockState);
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int i) {
                if (i == 0) return currentMode;
                return 0;
            }

            @Override
            public void set(int i, int i1) {
                if (i == 0) {
                    currentMode = (byte) i1;
                    if (level.getBlockEntity(pos) instanceof RefugeBlockEntity be) {
                        be.updateAllChunks((byte) i1);
                    }
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    @Override
    public DataComponentMap components() {
        return super.components();
    }

    public String[][] createPattern() {
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

        return sanitizePattern(pattern);
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

    private String flattenPattern(String[][] pattern) {
        String singleString = Arrays.stream(pattern)
                .flatMap(Arrays::stream)
                .collect(Collectors.joining(""));

        return singleString;
    }

    private String[][] decode(String string) {
        char[] chars = string.toCharArray();
        String[][] pattern = new String[6][10];

        for (int i = 0; i < pattern.length; i++) {
            for (int j = 0; j < pattern[i].length; j++) {
                int index = i * pattern[i].length + j;
                if (index < chars.length) {
                    pattern[i][j] = String.valueOf(chars[index]);
                } else {
                    pattern[i][j] = "O";
                }
            }
        }
        return pattern;
    }

    public void printPattern() {
        if (pattern == null) pattern = createPattern();

        for (int i = 0 ; i < pattern.length ; i++) {
            for (int j = pattern[i].length - 1; j >= 0 ; j--) {
                if (pattern[i][j].equals("X")) {

                    BlockPos pos = this.worldPosition.offset(i, j, 0);

                    if (level instanceof ServerLevel serverLevel)
                        serverLevel.sendParticles(ColorUtils.pixelVoidOptions, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), 10, 1, 1, 1, 0.05);

                    if (level.getBlockState(pos).isAir()) {
                        placeArm(pos);
                        return;
                    }
                    pos = this.worldPosition.offset(-i, j, 0);
                    if (level.getBlockState(pos).isAir()) {
                        placeArm(pos);
                        return;
                    }

                    pos = this.worldPosition.offset(0, j, i);
                    if (level.getBlockState(pos).isAir()) {
                        placeArm(pos);
                        return;
                    }

                    pos = this.worldPosition.offset(0, j, -i);
                    if (level.getBlockState(pos).isAir()) {
                        placeArm(pos);
                        return;
                    }
                }
            }
        }

        activate();
    }

    private void placeArm(BlockPos pos) {
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS);
        level.playSound(null, this.worldPosition, SoundEvents.AXE_STRIP, SoundSource.BLOCKS);
        level.setBlock(pos, BeyondBlocks.OBIROOT_ARM.get().defaultBlockState(), 3);
        if (level instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.OBIROOT.get().defaultBlockState()), pos.getX(), pos.getY(), pos.getZ(), 10, 0.5, 0.5, 0.5, 0.05);
    }

    private void activate() {
        level.playSound(null, this.worldPosition, SoundEvents.AXE_STRIP, SoundSource.BLOCKS);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.OBIROOT.get().defaultBlockState()), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), 120, 1f, 3, 1f, 0.05);
            serverLevel.sendParticles(ColorUtils.voidOptions, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), 12, 1f, 3, 1f, 0.05);

            TargetingConditions target = TargetingConditions.forNonCombat().range(32.0);
            AABB aabb = AABB.ofSize(this.worldPosition.getCenter(),32.0, 16.0, 32.0);
            List<Player> list1 = level.getNearbyPlayers(target, null, aabb);

            for (Player player : list1) {
                if (player != null && player instanceof ServerPlayer serverPlayer) {
                    BeyondCriteriaTriggers.COMPLETE_REFUGE.get().trigger(serverPlayer);
                }
            }
        }
        level.setBlock(this.worldPosition, BeyondBlocks.REFUGE.get().defaultBlockState().setValue(RefugeBlock.POWERED, true), 3);
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
        if (tag.contains("Pattern")) {
            this.pattern = decode(tag.getString("Pattern"));
        } else {
            this.pattern = createPattern();
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
        if (pattern != null) {
            tag.putString("Pattern", flattenPattern(pattern));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
                this.updateOwnerProfile();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        }
    }


    public void setOwner(@Nullable ResolvableProfile owner) {
        synchronized(this) {
            this.owner = owner;
        }

        this.updateOwnerProfile();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        level.playSound(null, this.worldPosition, SoundEvents.AXE_STRIP, SoundSource.BLOCKS);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BeyondBlocks.OBIROOT.get().defaultBlockState()), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), 120, 1f, 3, 1f, 0.05);
        }
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
        ChunkPos pos = getLevel().getChunk(worldPosition).getPos();
        int chunkX = pos.x;
        int chunkZ = pos.z;
        int chunkRadius = PROTECTION_RADIUS/2;

        BlockPos p = new BlockPos(chunkX*16, -47, chunkZ*16);

        //level.setBlock(p, Blocks.GLOWSTONE.defaultBlockState(), 3);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {

                int worldX = (chunkX + dx) * 16 + 8;
                int worldZ = (chunkZ + dz) * 16 + 8;

                if (Math.abs(worldX - worldPosition.getX()) <= PROTECTION_RADIUS * 16 && Math.abs(worldZ - worldPosition.getZ()) <= PROTECTION_RADIUS * 16) {

                    ChunkPos chunkPos = new ChunkPos(chunkX + dx, chunkZ + dz);
                    affectedChunks.add(chunkPos);
                    //level.setBlock(chunkPos.getMiddleBlockPosition(-52), Blocks.LIME_CONCRETE.defaultBlockState(), 3);

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
                if (newMode == -1){
                    if (currentMode > -1 && currentMode < 4) {
                        data.removeRefuge(currentMode);
                    }
                }
                else {
                    if (currentMode > -1 && currentMode < 4) {
                        data.removeRefuge(currentMode);
                    }
                    data.addRefuge(newMode);
                }
            }
        }

        currentMode = newMode;
        //fill();
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
                //level.setBlock(chunkPos.getMiddleBlockPosition(-48), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                //level.setBlock(chunkPos.getMiddleBlockPosition(-49), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                //level.setBlock(chunkPos.getMiddleBlockPosition(-50), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                //level.setBlock(chunkPos.getMiddleBlockPosition(-51), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                //level.setBlock(chunkPos.getMiddleBlockPosition(-52), Blocks.RED_CONCRETE.defaultBlockState(), 3);
            }
        }

    }

    private void fill() {
        if (level == null || level.isClientSide) return;

        int y = this.getBlockPos().getY();

        for (ChunkPos chunkPos : affectedChunks) {
            LevelChunk chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
            if (chunk != null) {
                RefugeChunkData data = chunk.getData(BeyondAttachments.REFUGE_DATA);
                //if (isActive) {

                if (data.shouldPreventHunger())
                    level.setBlock(chunkPos.getMiddleBlockPosition(y + 10), Blocks.BROWN_CONCRETE.defaultBlockState(), 3);
                if (data.shouldPreventExplosion())
                    level.setBlock(chunkPos.getMiddleBlockPosition(y + 11), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                if (data.shouldPreventMobSpawn())
                    level.setBlock(chunkPos.getMiddleBlockPosition(y + 12), Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
                if (data.shouldPreventFallDamage())
                    level.setBlock(chunkPos.getMiddleBlockPosition(y + 13), Blocks.BLUE_CONCRETE.defaultBlockState(), 3);


                //}else {
                if (!data.shouldPreventHunger())
                    level.setBlock(chunkPos.getMiddleBlockPosition(y + 10), Blocks.GLASS.defaultBlockState(), 3);
                if (!data.shouldPreventExplosion())
                    level.setBlock(chunkPos.getMiddleBlockPosition(y + 11), Blocks.GLASS.defaultBlockState(), 3);
                if (!data.shouldPreventMobSpawn())
                    level.setBlock(chunkPos.getMiddleBlockPosition(y + 12), Blocks.GLASS.defaultBlockState(), 3);
                if (!data.shouldPreventFallDamage())
                    level.setBlock(chunkPos.getMiddleBlockPosition(y + 13), Blocks.GLASS.defaultBlockState(), 3);
                //}
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RefugeBlockEntity be) {
        if (level.isClientSide) return;

        be.tickCounter++;

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


    public static void rotAnimationTick(Level level, BlockPos pos, BlockState state, RefugeBlockEntity be) {
        if (!RefugeBlock.isActive(state)) return;

        // Decrement animation counter on both sides
        if (be.animating > 0)
            be.animating--;

        be.oRot = be.rot;
        Player player = level.getNearestPlayer((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)0.5F, (double)pos.getZ() + (double)0.5F, (double)16.0F, false);

        if (player != null && level.random.nextFloat() < 0.01) {
            double d0 = player.getX() - ((double)pos.getX() + (double)0.5F);
            double d1 = player.getZ() - ((double)pos.getZ() + (double)0.5F);
            be.tRot = (float)Mth.atan2(d1, d0);
            level.playSound(player, pos, SoundEvents.ROOTS_BREAK, SoundSource.BLOCKS, 1, 1);
        }

        while(be.rot >= (float)Math.PI) {
            be.rot -= ((float)Math.PI * 2F);
        }

        while(be.rot < -(float)Math.PI) {
            be.rot += ((float)Math.PI * 2F);
        }

        while(be.tRot >= (float)Math.PI) {
            be.tRot -= ((float)Math.PI * 2F);
        }

        while(be.tRot < -(float)Math.PI) {
            be.tRot += ((float)Math.PI * 2F);
        }

        float f2;
        for(f2 = be.tRot - be.rot; f2 >= (float)Math.PI; f2 -= ((float)Math.PI * 2F)) {
        }

        while(f2 < -(float)Math.PI) {
            f2 += ((float)Math.PI * 2F);
        }

        be.rot += f2 * 0.4F;
        float f3 = 0.2F;
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

    protected void applyImplicitComponents(DataComponentInput componentInput) {
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
