package com.thebeyond.common.block.blockentities;

import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.client.menu.ProjectorMenu;
import com.thebeyond.common.block.ProjectorAcceptance;
import com.thebeyond.common.block.ProjectorBlock;
import com.thebeyond.common.data.BeyondDataMapTypes;
import com.thebeyond.common.data.ProjectorTexture;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondSoundEvents;
import com.thebeyond.data.BeyondDataMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static com.thebeyond.common.block.ProjectorBlock.FACING;

public class ProjectorBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOTS = 4;

    /** Client side only, walked by the renderer's per-pixel passes. */
    public static final java.util.Set<ProjectorBlockEntity> LOADED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (this.level != null && this.level.isClientSide) {
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    // the order is the wire contract (the ProjectorSetModePayload byte), keep it in step with menu, screen and renderer
    public static final int MODE_MIXUP = 0;
    public static final int MODE_CAROUSEL = 1;
    public static final int MODE_LINE = 2;
    public static final int MODE_QUADRANT = 3;

    // button labels in MODE order, raw strings so this class still loads on the server
    public static final String[] MODE_NAMES = {"Mix-up", "Carousel", "Line", "Quadrant"};

    private static final Component DEFAULT_NAME = Component.translatable("container.the_beyond.projector");

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private int mode = MODE_MIXUP;
    private int carouselIndex = 0;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> mode;
                case 1 -> carouselIndex;
                case 2 -> isLit();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> mode = v;
                case 1 -> carouselIndex = v;
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public ProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(BeyondBlockEntities.PROJECTOR.get(), pos, state);
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    /** Slot order is a contract: the renderer's mode math indexes into this. */
    public int[] filledSlots() {
        int n = 0;
        int[] tmp = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                tmp[n++] = i;
            }
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    public int getMode() {
        return mode;
    }

    public int getCarouselIndex() {
        return carouselIndex;
    }

    public int isLit() {
        Level level = this.getLevel();
        if (level == null) {
            return 0;
        }
        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof ProjectorBlock)) {
            return 0;
        }

        return state.getValue(ProjectorBlock.POWERED) ? 1 : 0;
    }

    /** No clamp: render wraps the index modulo the filled-slot count. */
    public void advanceCarousel() {
        carouselIndex++;
        setChanged();
    }

    public void setMode(int mode) {
        this.mode = Math.floorMod(mode, 4);
        setChanged();
    }

    public void stepCarousel(int delta) {
        this.carouselIndex += delta;
        setChanged();
    }

    public boolean isGroupComplete(ResourceLocation group) {
        if (items.isEmpty()) return false;
        if (items.contains(ItemStack.EMPTY)) return false;

        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            ProjectorTexture pt = BeyondDataMapTypes.getProjectorTexture(stack);
            if (pt == null || pt.composeGroup().isEmpty() || !pt.composeGroup().get().equals(group)) {
                return false;
            }
        }
        return true;
    }

    private void checkRevealOnChange(ResourceLocation group, boolean wasComplete) {
        if (group != null && isGroupComplete(group) && !wasComplete && level != null) {
            BlockState state = getBlockState();
            BlockPos front = ProjectorBlock.frontOrigin(getBlockPos(), state);
            Vec3 c = BeyondCompatHooks.visibleOrCenter(level, front);

            if (level instanceof ServerLevel serverLevel) {
                for (ServerPlayer player : serverLevel.getPlayers(p -> p.distanceToSqr(getBlockPos().getCenter()) < 8 * 8)) {
                    BeyondCriteriaTriggers.DISCOVER_PROJECTION.get().trigger(player);
                    if (group.equals(BeyondDataMaps.HISTORY)) {
                        BeyondCriteriaTriggers.DISCOVER_ALL_PROJECTION_0.get().trigger(player);
                    } else if (group.equals(BeyondDataMaps.KEY)) {
                        BeyondCriteriaTriggers.DISCOVER_ALL_PROJECTION_1.get().trigger(player);
                    } else if (group.equals(BeyondDataMaps.PRISON)) {
                        BeyondCriteriaTriggers.DISCOVER_ALL_PROJECTION_2.get().trigger(player);
                    } else if (group.equals(BeyondDataMaps.PUNISHMENT)) {
                        BeyondCriteriaTriggers.DISCOVER_ALL_PROJECTION_3.get().trigger(player);
                    }
                }
            }

            if (!level.isClientSide) {
                level.playSound(null, c.x, c.y, c.z, BeyondSoundEvents.PROJECTOR_MURMUR, SoundSource.BLOCKS, 0.9f, 0.9f + level.random.nextFloat() * 0.3f);
            } else {
                for (int i = 0; i < 4; i++) {
                    level.addParticle(BeyondParticleTypes.SOUL.get(), c.x + 1 - level.random.nextFloat()*2, c.y + 1 - level.random.nextFloat()*2, c.z + 1 - level.random.nextFloat()*2, state.getValue(FACING).getStepX()*0.1f, (double) 0.01F, state.getValue(FACING).getStepZ()*0.1f);
                }
            }
        }
    }

    @Nullable
    private static ResourceLocation groupOf(ItemStack stack) {
        ProjectorTexture pt = BeyondDataMapTypes.getProjectorTexture(stack);
        return (pt != null && pt.composeGroup().isPresent()) ? pt.composeGroup().get() : null;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, ProjectorBlockEntity be) {

    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack s = ContainerHelper.removeItem(items, slot, amount);
        if (!s.isEmpty()) {
            setChanged();
        }
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ResourceLocation group = groupOf(stack);
        boolean wasComplete = group != null && isGroupComplete(group);
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));

        if (level instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.getPlayers(p -> p.distanceToSqr(getBlockPos().getCenter()) < 4 * 4)) {
                BeyondCriteriaTriggers.FILL_PROJECTOR.get().trigger(player);
            }
        }

        setChanged();
        checkRevealOnChange(group, wasComplete);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return ProjectorAcceptance.accepts(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        clearSlots();
    }

    /** clear() throws on the fixed-size NonNullList, so the slots reset in place. */
    private void clearSlots() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clearSlots();  // loadAllItems writes only present slots, so an emptied slot would stay stale on the client
        ContainerHelper.loadAllItems(tag, items, registries);
        mode = tag.getInt("Mode");
        carouselIndex = tag.getInt("CarouselIndex");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Mode", mode);
        tag.putInt("CarouselIndex", carouselIndex);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return DEFAULT_NAME;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ProjectorMenu(containerId, inventory, this, this.dataAccess, this.getBlockPos());
    }
}
