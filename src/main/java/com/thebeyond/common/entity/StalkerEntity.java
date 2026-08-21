package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StalkerEntity extends LivingEntity implements OwnableEntity {
    private static final EntityDataAccessor<Integer> GENERATION = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Direction> FACING = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Boolean> VIOLENT = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public boolean markedForRemoval = false;
    private static final byte ATTACK = 67;
    public static final byte SPREAD = 68;
    private static final byte RETREAT = 69;
    private int retreatCounter = 0;
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState spreadAnimationState = new AnimationState();
    public final AnimationState retreatAnimationState = new AnimationState();
    public int children = 0;
    public boolean base = false;

    public List<Direction> directions = new ArrayList<>(List.of(Direction.values()));
    public static final int MAX_GENERATION = 10;
    public boolean yetToBud = true;
    public StalkerEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.MAX_HEALTH, 10);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GENERATION, 0);
        builder.define(FACING, Direction.NORTH);
        builder.define(VIOLENT, false);
        builder.define(OWNER, Optional.empty());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        entityData.set(GENERATION, compound.getInt("Generation"));
        yetToBud = compound.getBoolean("YetToBud");
        entityData.set(FACING, Direction.from3DDataValue(compound.getByte("Facing")));
        entityData.set(VIOLENT, compound.getBoolean("Violent"));
        markedForRemoval = compound.getBoolean("Violent");

        UUID uuid;
        if (compound.hasUUID("Owner")) {
            uuid = compound.getUUID("Owner");
            entityData.set(OWNER, Optional.ofNullable(uuid));
        }
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Generation", entityData.get(GENERATION));
        compound.putBoolean("YetToBud", yetToBud);
        compound.putByte("Facing", (byte)entityData.get(FACING).get3DDataValue());
        compound.putBoolean("Violent", entityData.get(VIOLENT));
        compound.putBoolean("MarkedForRemoval", markedForRemoval);
        if (this.getOwnerUUID() != null) {
            compound.putUUID("Owner", this.getOwnerUUID());
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
        super.knockback(0, x, z);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (this.level().isClientSide) {
            if (id == SPREAD) {
                this.spreadAnimationState.start(this.tickCount);
                return;
            }
            if (id == ATTACK) {
                this.attackAnimationState.start(this.tickCount);
                return;
            }
            if (id == RETREAT) {
                this.retreatAnimationState.start(this.tickCount);
                return;
            }
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason != Entity.RemovalReason.DISCARDED) {
            StalkerEntity owner = (StalkerEntity) this.getOwner();
            if (owner != null) {
                owner.markedForRemoval = true;
                owner.children--;
            }
        }

        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().getDifficulty() == Difficulty.PEACEFUL) return;

        if (!level().isClientSide) {

            if (!base && children <= 0 && !isViolent() && (tickCount % 60 == 0)) markedForRemoval = true;

            if (retreatCounter==0 && (tickCount % 100 == 0)) {
                if (level().getBlockState(this.blockPosition().offset(getFacing().getStepX(), getFacing().getStepY(), getFacing().getStepZ())).isSolid()) markedForRemoval = true;
            }
            if (markedForRemoval && retreatCounter==0 && (tickCount % 10 == 0)) {
                StalkerEntity owner = (StalkerEntity) this.getOwner();
                if (owner != null) {
                    owner.markedForRemoval = true;
                }
                beginRetreat();
            }
            if (retreatCounter > 0) {
                if (retreatCounter == 7) level().broadcastEntityEvent(this, RETREAT);
                retreatCounter--;
                if (retreatCounter == 1) {
                    StalkerEntity owner = (StalkerEntity) this.getOwner();
                    if (owner != null) {
                        owner.children--;
                    }
                    this.discard();
                }
            }

            if (isViolent() && (tickCount % 80 == 0)) {
                markedForRemoval = true;
            }

            if (yetToBud && getGeneration() > MAX_GENERATION) attack();
            boolean b = (this.tickCount % 3) == 0;

            if (yetToBud && b) {
                Vec3 delta = getTargetDelta();
                Direction d = getTargetDirection(delta);

                if (d == null) {
                    yetToBud = false;
                    markedForRemoval = true;
                    return;
                }

                if (delta != null && delta.length() < 2) {
                    attack();
                    return;
                }

                BlockPos pos = this.blockPosition();
                if (isSpaceOccupied(pos, d)) {
                    spawnChild(pos, d);
                    if (random.nextInt(10)==0) spawnChild(pos, getTargetDirection(delta));
                } else {
                    level().playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE);
                }
            }
        } else {
            BlockPos offset = this.blockPosition().offset(getFacing().getStepX(), getFacing().getStepY(), getFacing().getStepZ());
            if (level().getBlockState(offset).isSolid()) {
                for(int i = 0; i < 30; ++i) {
                    Vec3 vec3 = Vec3.atCenterOf(offset).subtract(getFacing().getStepX()*0.5f, getFacing().getStepY()*0.5f, getFacing().getStepZ()*0.5f);
                    this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(offset)), vec3.x, vec3.y, vec3.z, (double)0.0F, (double)0.0F, (double)0.0F);
                }
            }
        }
    }

    private void beginRetreat() {
        if (children <= 0)
            this.retreatCounter = 7;
    }

    private void spawnChild(BlockPos pos, Direction d) {
        StalkerEntity stalker = new StalkerEntity(BeyondEntityTypes.STALKER.get(), level());

        Direction direction = entityData.get(FACING);

        BlockPos newPos = pos.offset(direction.getStepX(), direction.getStepY(), direction.getStepZ()).offset(d.getStepX(), (d.getStepY()), d.getStepZ());
        stalker.setPos(newPos.getX() + 0.5f, newPos.getY(), newPos.getZ() + 0.5f);
        stalker.setFacing(d);
        stalker.setGeneration(getGeneration()+1);
        level().addFreshEntity(stalker);
        stalker.setOwner(this.getUUID());
        stalker.level().broadcastEntityEvent(stalker, SPREAD);

        this.children++;
        yetToBud = false;
        level().playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BEEHIVE_EXIT, SoundSource.HOSTILE);
    }

    private void attack() {
        setViolent(true);
        yetToBud = false;
        level().broadcastEntityEvent(this, ATTACK);
        level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE);
    }

    private Vec3 getTargetDelta() {
        LivingEntity target = level().getNearestPlayer(this, 20);
        if (target == null) return null;
        return target.position().subtract(this.position());
    }

    private Direction getTargetDirection(Vec3 delta) {
        Direction direction = directions.isEmpty() ? null : directions.get(level().random.nextInt(directions.size()));
        if (delta == null) {
            return direction;
        }

        Direction nearest = Direction.getNearest(delta);
        if (directions.contains(nearest)) {
            directions.remove(nearest);
            return nearest;
        }
        return direction;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    private boolean isSpaceOccupied(BlockPos pos, Direction d) {
        BlockPos tailPos = pos.offset(d.getNormal());
        BlockPos headPos = pos.offset(d.getNormal().multiply(2));

        if (level().getBlockState(tailPos).isSolid()) return false;
        if (level().getBlockState(headPos).isSolid()) return false;
        if (!level().getEntitiesOfClass(StalkerEntity.class, new AABB(tailPos)).isEmpty()) return false;
        if (!level().getEntitiesOfClass(StalkerEntity.class, new AABB(headPos)).isEmpty()) return false;

        return true;
    }

    public void setFacing(Direction direction) {
        entityData.set(FACING, direction);
    }

    public Direction getFacing() {
        return this.entityData.get(FACING);
    }

    public void setGeneration(int generation) {
        entityData.set(GENERATION, generation);
    }

    public int getGeneration() {
        return this.entityData.get(GENERATION);
    }

    public void setViolent(boolean violent) {
        entityData.set(VIOLENT, violent);
    }

    public boolean isViolent() {
        return this.entityData.get(VIOLENT);
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return null;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot equipmentSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot equipmentSlot, ItemStack itemStack) {

    }

    @Override
    public HumanoidArm getMainArm() {
        return null;
    }

    @Override
    public @Nullable UUID getOwnerUUID() {
        Optional<UUID> uuid1 = entityData.get(OWNER);
        return uuid1.orElse(null);
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        if (level() instanceof ServerLevel serverLevel)
            return (LivingEntity) serverLevel.getEntity(getOwnerUUID());
        return null;
    }

    public void setOwner(UUID uuid) {
        entityData.set(OWNER, Optional.ofNullable(uuid));
    }
}
