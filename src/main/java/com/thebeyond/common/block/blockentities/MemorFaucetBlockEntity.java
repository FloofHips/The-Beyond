package com.thebeyond.common.block.blockentities;

import com.thebeyond.client.particle.CircleColorTransitionOptions;
import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.common.block.MemorFaucetBlock;
import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.*;
import net.minecraft.server.level.ServerPlayer;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.thebeyond.common.block.BonfireBlock.LIT;
import static com.thebeyond.common.block.MemorFaucetBlock.AGE;

public class MemorFaucetBlockEntity extends BlockEntity implements Container {
    private static final int CHECK_INTERVAL = 50;
    private static final int DETECTION_RANGE = 5;
    private static final int NOMAD_RANGE = 32;
    private int tickCounter = 0;
    public float activeProgress = 0;
    private boolean active = false;
    private NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
    public int birthday = 0;

    public MemorFaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        setItems();
    }

    public MemorFaucetBlockEntity(BlockPos pos, BlockState blockState) {
        this(BeyondBlockEntities.MEMOR_FAUCET.get(), pos, blockState);
        setItems();
    }

    public int getBirthday() {
        return birthday;
    }

    public boolean isItMyBirthdayToday() {
        int birthday = getBirthday();

        if (birthday != 0 && level != null) {
            return ((level.getDayTime() / 24000)) > birthday + 15;
        }
        return false;
    }

    /** Visible center; identity outside sub-levels. */
    private static BlockPos anchor(Level level, BlockPos pos) {
        Vec3 v = BeyondCompatHooks.visibleOrCenter(level, pos);
        return BlockPos.containing(v);
    }

    private static void safeSendBlockUpdated(Level level, BlockPos pos, BlockState state) {
        try {
            level.sendBlockUpdated(pos, state, state, 3);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MemorFaucetBlockEntity be) {
        if (state.getValue(AGE) == MemorFaucetBlock.MAX_AGE && be.activeProgress == 0) return;

        if (be.active) {
            be.activeProgress = Mth.lerp(0.2f, be.activeProgress, 1);
        } else {
            be.activeProgress = Mth.lerp(0.1f, be.activeProgress, 0);
        }

        be.tickCounter++;

        if (be.tickCounter == CHECK_INTERVAL) {
            be.checkForActivation(level, pos);
            be.consumeItems(level, pos);
        }

        if (be.activeProgress > 0 && be.activeProgress < 1) {
            be.setChanged();
            if (!level.isClientSide) {
                safeSendBlockUpdated(level, pos, state);
            }
        }

        if (be.tickCounter == CHECK_INTERVAL + 5) {
            be.tickCounter = 0;
            BlockPos a = anchor(level, pos);
            AABB detectionBox = new AABB(a).inflate(20);

            if (level.getBlockState(pos).getValue(AGE) == 0) {
                List<AbyssalNomadEntity> entities = level.getEntitiesOfClass(AbyssalNomadEntity.class, detectionBox);

                for (AbyssalNomadEntity nomad : entities) {
                    Vec3 newPos = a.getCenter().subtract(nomad.position()).normalize().scale(15);
                    nomad.prayerSite = a.offset((int) newPos.x, 0, (int) newPos.z);
                }
            }

            if (level.getBlockState(pos).getValue(AGE) == 0) {
                TargetingConditions conditions = TargetingConditions.forNonCombat().range(6).ignoreLineOfSight().selector((entity) -> entity instanceof AbyssalNomadEntity nomad);
                AbyssalNomadEntity nearestNomad = (AbyssalNomadEntity) level.getNearestEntity(AbyssalNomadEntity.class, conditions, null, a.getX(), a.getY(), a.getZ(), detectionBox);

                if (nearestNomad!=null && a.getCenter().subtract(nearestNomad.position()).length() < 10)
                    be.spawnItemFromNomad(nearestNomad, a);
            }
        }
    }

    private void checkForActivation(Level level, BlockPos pos) {
        BlockPos a = anchor(level, pos);
        AABB detectionBox = new AABB(a).inflate(DETECTION_RANGE);

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, detectionBox);

        for (LivingEntity entity : entities) {
            if (shouldActivate(entity)) {
                activateFaucet(level, pos);
                break;
            } else {
                deactivateFaucet(level, pos);
                break;
            }
        }
    }

    private void spawnItemFromNomad(AbyssalNomadEntity nomad, BlockPos targetPos) {
        ItemStack item = new ItemStack(BeyondItems.REMEMBRANCE_CLOTH.get());

        if (!item.isEmpty()) {
            Vec3 startPos = nomad.position().add(0, nomad.getEyeHeight()+1, 0);
            Vec3 endPos = Vec3.atCenterOf(targetPos).add(0, -0.5, 0);

            nomad.level().broadcastEntityEvent(nomad, (byte) 69);

            ItemEntity itemEntity = new ItemEntity(nomad.level(), startPos.x, startPos.y, startPos.z, item);

            double dx = endPos.x - startPos.x;
            double dy = endPos.y - startPos.y;
            double dz = endPos.z - startPos.z;

            itemEntity.setNeverPickUp();
            itemEntity.setDeltaMovement(dx * 0.1, Math.max(0.2, dy * 0.05 + 0.2), dz * 0.1);

            nomad.level().addFreshEntity(itemEntity);
        }
    }

    private boolean shouldActivate(LivingEntity entity) {
        if (entity instanceof AbyssalNomadEntity) {
            return true;
        }

        if (entity instanceof Player player) {
            return player.getMainHandItem().is(BeyondTags.REMEMBRANCES) ||
                    player.getOffhandItem().is(BeyondTags.REMEMBRANCES);
        }

        return false;
    }

    private void activateFaucet(Level level, BlockPos pos) {
        setItems();
        if (active) return;
        active = true;
        BlockPos a = anchor(level, pos);
        level.playSound(null, a, BeyondSoundEvents.MEMOR_FAUCET_OPEN.get(), SoundSource.BLOCKS, 1.0F, 0.8f + level.random.nextFloat() * 0.5f);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT, a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5, 10, 0.5, 0.5, 0.5, 0.1);
        }

        setChanged();
        if (!level.isClientSide) {
            safeSendBlockUpdated(level, pos, getBlockState());
        }
    }

    private void deactivateFaucet(Level level, BlockPos pos) {
        if (!active) return;
        active = false;
        level.playSound(null, anchor(level, pos), BeyondSoundEvents.MEMOR_FAUCET_CLOSE.get(), SoundSource.BLOCKS, 1.0F, 0.8f + level.random.nextFloat() * 0.5f);

        setChanged();
        if (!level.isClientSide) {
            safeSendBlockUpdated(level, pos, getBlockState());
        }
    }

    public float getActiveProgress() {
        return activeProgress;
    }

    public boolean isActive() {
        return active;
    }

    private void setItems() {
        Components.DynamicColorComponent colors = new Components.DynamicColorComponent(0.5f, 1.7f, 1.9f, 0.8f, 0, 0.2f, 0, 0.2f, 0xF000F0);

        List<Item> remembranceItems = new ArrayList<>();

        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(BeyondTags.REMEMBRANCES)) {
             remembranceItems.add(holder.value());
        }

        if (remembranceItems.isEmpty()) {
            ItemStack fallbackStack = BeyondItems.REMEMBRANCE_LACE.toStack();
            fallbackStack.set(BeyondComponents.COLOR_COMPONENT, colors);

            for (int i = 0; i < getContainerSize(); i++) {
                setItem(i, fallbackStack.copy());
            }
            return;
        } else {
            Collections.shuffle(remembranceItems);

            for (int i = 0; i < getContainerSize(); i++) {
                Item remembranceItem = remembranceItems.get(i % remembranceItems.size());
                ItemStack stack = new ItemStack(remembranceItem);

                stack.set(BeyondComponents.COLOR_COMPONENT, colors);
                setItem(i, stack);
            }
        }
    }

    private void consumeItems(Level level, BlockPos pos) {
        BlockPos a = anchor(level, pos);
        AABB itemBB = new AABB(a).inflate(2.0).move(0,-2.5,0);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, itemBB);

        for (ItemEntity itemEntity : items) {
            ItemStack itemStack = itemEntity.getItem();

            if (!itemStack.is(BeyondTags.REMEMBRANCES)) {
                continue;
            }

            if (itemStack.getCount() > 1) {
                itemStack.shrink(1);

                ItemStack remainingStack = itemStack.copy();
                itemEntity.setItem(remainingStack);
            } else {
                itemEntity.discard();
            }

            level.playSound(null, a, BeyondSoundEvents.MEMOR_FAUCET_ABSORB.get(), SoundSource.BLOCKS, 1, 1);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), a.getX() + 0.5, a.getY() - 0.5, a.getZ() + 0.5, 1, 0, 0, 0, 0);
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), itemEntity.position().x, itemEntity.position().y + 0.1, itemEntity.position().z, 1, 0, 0, 0, 0);
            }

            increaseAge(level, pos);
            break;
        }
    }

    private void increaseAge(Level level, BlockPos pos) {
        BlockState currentState = level.getBlockState(pos);
        int currentAge = currentState.getValue(AGE);
        BlockPos a = anchor(level, pos);

        if (currentAge < MemorFaucetBlock.MAX_AGE) {
            int newAge = currentAge + 1;
            level.setBlock(pos, currentState.setValue(AGE, newAge), 3);

            if (newAge == 1) {
                level.playSound(null, a, BeyondSoundEvents.MEMOR_FAUCET_POWER1.get(), SoundSource.BLOCKS, 1, 1);
                spawnNomads(level, pos);
                affectNomads(level, pos, (byte) 2);
                return;
            }

            if (newAge == 2) {
                level.playSound(null, a, BeyondSoundEvents.MEMOR_FAUCET_POWER2.get(), SoundSource.BLOCKS, 1, 1);
                affectNomads(level, pos, (byte) 3);
                return;
            }

            if (newAge == 3) {
                level.playSound(null, a, BeyondSoundEvents.MEMOR_FAUCET_POWER3.get(), SoundSource.BLOCKS, 1, 1);
                affectNomads(level, pos, (byte) 3);
                return;
            }

            if (newAge == 4) {
                level.playSound(null, a, BeyondSoundEvents.MEMOR_FAUCET_POWER4.get(), SoundSource.BLOCKS, 1, 1);
                affectNomads(level, pos, (byte) 3);
                return;
            }

            if (newAge >= MemorFaucetBlock.MAX_AGE) {
                level.playSound(null, a, BeyondSoundEvents.MEMOR_FAUCET_POWER_UP.get(), SoundSource.BLOCKS, 1, 1);
                affectNomads(level, pos, (byte) 4);
                return;
            }
        }
    }

    private void spawnNomads(Level level, BlockPos pos) {
        BlockPos a = anchor(level, pos);
        AABB box = new AABB(a).inflate(NOMAD_RANGE);
        List<AbyssalNomadEntity> nomads = level.getEntitiesOfClass(AbyssalNomadEntity.class, box);

        int i1 = Math.max((2 + level.random.nextInt(10)) - nomads.size(),0);
        if (i1 == 0) return;

        Direction direction = level.getBlockState(pos).getValue(MemorFaucetBlock.FACING);

        // Stationary: spawn deep in The Paths and walk up via prayerSite. On a balloon:
        // probe for a sturdy block near storage pos and spawn on top, projected to visible.
        boolean inSubLevel = com.thebeyond.api.compat.BeyondCompatHooks.visibleOnly(level, pos) != null;

        for (int i = 0; i < i1; i++) {
            if (inSubLevel) {
                BlockPos ground = findGroundNear(level, pos, 4, 8);
                if (ground == null) continue;
                Vec3 spawn = com.thebeyond.api.compat.BeyondCompatHooks.visibleOrCenter(level, ground.above());
                AbyssalNomadEntity nomad = new AbyssalNomadEntity(BeyondEntityTypes.ABYSSAL_NOMAD.get(), level);
                nomad.setPos(spawn.x, spawn.y, spawn.z);
                level.addFreshEntity(nomad);
            } else {
                BlockPos auroracite = findAuroraciteNear(level, a.relative(direction, 10),
                        level.getMinBuildHeight() + 1, 10, 8);
                if (auroracite == null) continue;
                AbyssalNomadEntity nomad = new AbyssalNomadEntity(BeyondEntityTypes.ABYSSAL_NOMAD.get(), level);
                nomad.setPos(auroracite.getX() + 0.5, auroracite.getY() + 1, auroracite.getZ() + 0.5);
                level.addFreshEntity(nomad);
            }
        }
    }

    /** First sturdy-top block within hRadius/vDepth of center; spawn anchor is one above. */
    private static BlockPos findGroundNear(Level level, BlockPos center, int hRadius, int vDepth) {
        int dx = level.random.nextInt(hRadius * 2 + 1) - hRadius;
        int dz = level.random.nextInt(hRadius * 2 + 1) - hRadius;
        BlockPos cursor = center.offset(dx, 1, dz);
        for (int y = 0; y <= vDepth; y++) {
            BlockPos p = cursor.below(y);
            if (level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)) return p;
        }
        return null;
    }

    private static BlockPos findAuroraciteNear(Level level, BlockPos center, int layerTopY, int hRadius, int attempts) {
        for (int n = 0; n < attempts; n++) {
            int dx = level.random.nextInt(hRadius * 2 + 1) - hRadius;
            int dz = level.random.nextInt(hRadius * 2 + 1) - hRadius;
            BlockPos p = new BlockPos(center.getX() + dx, layerTopY, center.getZ() + dz);
            if (level.getBlockState(p).is(BeyondBlocks.AURORACITE)) return p;
        }
        return null;
    }

    private void affectNomads(Level level, BlockPos pos, byte b) {
        BlockPos a = anchor(level, pos);
        AABB box = new AABB(a).inflate(NOMAD_RANGE);
        List<AbyssalNomadEntity> nomads = level.getEntitiesOfClass(AbyssalNomadEntity.class, box);

        if (b == (byte) 1) {
            for (AbyssalNomadEntity nomad : nomads) {
                nomad.lookAt = a;
                Vec3 distance = nomad.position().subtract(Vec3.atCenterOf(a));
                Vec3 dest = distance.normalize().scale(10);

                Vec3 newPos = Vec3.atCenterOf(a).add(dest);
                nomad.getNavigation().moveTo(newPos.x, newPos.y, newPos.z, 0.5 + nomad.level().random.nextFloat());
            }
        }

        if (b == (byte) 2) {
            for (AbyssalNomadEntity nomad : nomads) {
                nomad.lookAt = a;
                nomad.sitDownCounter = 60 + nomad.level().random.nextInt(0, 20);
            }
        }

        if (b == (byte) 3) {
            for (AbyssalNomadEntity nomad : nomads) {
                nomad.lookAt = a;
                nomad.level().broadcastEntityEvent(nomad, (byte) 69);
                level.playSound(null, a, BeyondSoundEvents.ABYSSAL_NOMAD_NOD.get(), SoundSource.BLOCKS, 1, 1);
            }
        }

        if (b == (byte) 4) {
            this.birthday = (int) (level.getDayTime() / 24000);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(new CircleColorTransitionOptions(
                        new Vector3f(0.0f, 0.9f, 0.9f),
                        new Vector3f(0.0f, 0.5f, 0.5f),
                        0.5f
                ), a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5, 1, 0, 0, 0, 0.05);

                serverLevel.sendParticles(new CircleColorTransitionOptions(
                        new Vector3f(0.0f, 0.9f, 0.9f),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        0.8f
                ), a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5, 1, 0, 0, 0, 0.05);
            }

            //level.playSound(null, a, BeyondSoundEvents.MEMOR_FAUCET_OPEN.get(), SoundSource.BLOCKS, 1, 1);

            for (AbyssalNomadEntity nomad : nomads) {
                nomad.lookAt = a;
                //level.playSound(null, nomad.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.5F, 0.8F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), nomad.position().x, nomad.position().y + nomad.getEyeHeight() + 0.1, nomad.position().z, 1, 0, 0, 0, 0);
                }
                nomad.dropCounter = 60 + nomad.level().random.nextInt(0, 20);
                level.playSound(null, a, BeyondSoundEvents.ABYSSAL_NOMAD_THANK.get(), SoundSource.BLOCKS, 1, 1);
            }

            Player nearestPlayer = level.getNearestPlayer(a.getX(), a.getY(), a.getZ(), 16, false);
            if (nearestPlayer instanceof ServerPlayer serverPlayer) {
                BeyondCriteriaTriggers.FOUNTAIN_OFFERING.get().trigger(serverPlayer);
            }
        }
    }

    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    public int getContainerSize() {
        return 5;
    }

    @Override
    public boolean isEmpty() {
        for(ItemStack itemstack : this.getItems()) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.getItems().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemstack = ContainerHelper.removeItem(this.getItems(), slot, amount);
        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.getItems(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.getItems().set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.getItems().clear();
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }


    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        active = tag.getBoolean("Active");
        activeProgress = tag.getFloat("ActiveProgress");
        ContainerHelper.loadAllItems(tag, items, registries);
        birthday = tag.getInt("Birthday");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Active", active);
        tag.putFloat("ActiveProgress", activeProgress);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (getBlockState().getValue(AGE)==5) {
            tag.putInt("Birthday", birthday);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Active", active);
        tag.putFloat("ActiveProgress", activeProgress);
        ContainerHelper.saveAllItems(tag, items, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}