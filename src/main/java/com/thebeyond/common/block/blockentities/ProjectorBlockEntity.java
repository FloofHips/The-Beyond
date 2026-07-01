package com.thebeyond.common.block.blockentities;

import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.common.block.ProjectorAcceptance;
import com.thebeyond.common.block.ProjectorBlock;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.data.BeyondDataMapTypes;
import com.thebeyond.common.data.ProjectorTexture;
import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ProjectorBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOTS = 4;

    /** Client-side only; iterated by the renderer's per-pixel passes. */
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

    // Must match ProjectorMenu, ProjectorScreen, and the renderer. Order is the wire contract: the index is cast to the ProjectorSetModePayload byte.
    public static final int MODE_MIXUP = 0;
    public static final int MODE_CAROUSEL = 1;
    public static final int MODE_LINE = 2;
    public static final int MODE_QUADRANT = 3;
    // Button labels, positionally aligned to the MODE_* values above. Raw strings (not Component) so this class still loads server-side.
    public static final String[] MODE_NAMES = {"Mix-up", "Carousel", "Line", "Quadrant"};

    private static final Component DEFAULT_NAME = Component.translatable("container.the_beyond.projector");

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private int mode = MODE_MIXUP;
    private int carouselIndex = 0;
    private boolean carouselAuto = false;
    private int carouselPeriod = 40; // ticks
    private boolean flipped = false;
    private int rotation = 0;         // quarter-turns 0..3, independent of world facing
    private ResourceLocation gradeId = Grades.AS_PHOTO;

    private int tickCounter;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> mode;
                case 1 -> carouselIndex;
                case 2 -> carouselAuto ? 1 : 0;
                case 3 -> carouselPeriod;
                case 4 -> flipped ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> mode = v;
                case 1 -> carouselIndex = v;
                case 2 -> carouselAuto = v != 0;
                case 3 -> carouselPeriod = v;
                case 4 -> flipped = v != 0;
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

    public int lastOccupiedSlot() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
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

    public boolean isCarouselAuto() {
        return carouselAuto;
    }

    public int getCarouselPeriod() {
        return carouselPeriod;
    }

    public boolean isFlipped() {
        return flipped;
    }

    public int getRotation() {
        return rotation;
    }

    public void addRotation(int steps) {
        this.rotation = Math.floorMod(this.rotation + steps, 4);
        setChanged();
    }

    public ResourceLocation getGradeId() {
        return gradeId;
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

    public void setCarouselAuto(boolean auto) {
        this.carouselAuto = auto;
        setChanged();
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        setChanged();
    }

    public void toggleFlipped() {
        setFlipped(!this.flipped);
    }

    public void setGradeId(ResourceLocation id) {
        this.gradeId = id;
        setChanged();
    }

    public boolean isGroupComplete(ResourceLocation group) {
        final int n = 8;
        boolean[] covered = new boolean[n * n];
        int marked = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            ProjectorTexture pt = BeyondDataMapTypes.getProjectorTexture(stack);
            if (pt == null || pt.composeGroup().isEmpty() || !pt.composeGroup().get().equals(group)) {
                continue;
            }
            ProjectorTexture.Region r = pt.region();
            // Half-open [u0,u1)x[v0,v1): must match the renderer's shared-edge boundary rule.
            for (int gy = 0; gy < n; gy++) {
                float cy = (gy + 0.5f) / n;
                if (cy < r.v0() || cy >= r.v1()) {
                    continue;
                }
                for (int gx = 0; gx < n; gx++) {
                    float cx = (gx + 0.5f) / n;
                    if (cx < r.u0() || cx >= r.u1()) {
                        continue;
                    }
                    int idx = gy * n + gx;
                    if (!covered[idx]) {
                        covered[idx] = true;
                        marked++;
                    }
                }
            }
        }
        return marked == n * n;
    }

    private void checkRevealOnChange(ResourceLocation group, boolean wasComplete) {
        if (group != null && !wasComplete && isGroupComplete(group) && level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            BlockPos front = ProjectorBlock.frontOrigin(getBlockPos(), state);
            Vec3 c = BeyondCompatHooks.visibleOrCenter(level, front);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9f, 1.2f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.5, c.z, 24, 0.4, 0.4, 0.4, 0.02);
            }
        }
    }

    @Nullable
    private static ResourceLocation groupOf(ItemStack stack) {
        ProjectorTexture pt = BeyondDataMapTypes.getProjectorTexture(stack);
        return (pt != null && pt.composeGroup().isPresent()) ? pt.composeGroup().get() : null;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, ProjectorBlockEntity be) {
        if (!be.carouselAuto || be.mode != MODE_CAROUSEL || be.carouselPeriod <= 0) {
            return;
        }
        if (++be.tickCounter % be.carouselPeriod == 0) {
            be.advanceCarousel();
        }
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

    /** {@code clear()} throws on the fixed-size NonNullList; reset in place instead. */
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
        clearSlots(); // loadAllItems only writes present slots; reset first or an emptied slot stays stale clientside
        ContainerHelper.loadAllItems(tag, items, registries);
        mode = tag.getInt("Mode");
        carouselIndex = tag.getInt("CarouselIndex");
        carouselAuto = tag.getBoolean("CarouselAuto");
        carouselPeriod = tag.contains("CarouselPeriod") ? tag.getInt("CarouselPeriod") : 40;
        flipped = tag.getBoolean("Flipped");
        rotation = Math.floorMod(tag.getInt("Rotation"), 4);
        gradeId = gradeIdFromNbt(tag.getString("Grade"));
    }

    /** Legacy enum names migrate to built-in grade ids; new saves store the id directly. */
    private static ResourceLocation gradeIdFromNbt(String s) {
        if (s == null || s.isEmpty()) {
            return Grades.AS_PHOTO;
        }
        switch (s) {
            case "NONE": return Grades.NONE;
            case "SEPIA": return Grades.SEPIA;
            case "BLUE": return Grades.BLUE;
            default:
                ResourceLocation rl = ResourceLocation.tryParse(s);
                return rl != null ? rl : Grades.AS_PHOTO;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Mode", mode);
        tag.putInt("CarouselIndex", carouselIndex);
        tag.putBoolean("CarouselAuto", carouselAuto);
        tag.putInt("CarouselPeriod", carouselPeriod);
        tag.putBoolean("Flipped", flipped);
        tag.putInt("Rotation", rotation);
        tag.putString("Grade", gradeId.toString());
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
