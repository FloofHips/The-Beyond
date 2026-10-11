package com.thebeyond.common.entity;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.client.particle.CloudColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.movement.Target;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.awt.*;
import java.util.List;

public class BaubleEntity extends LivingBlock {
    public boolean seekingFusion = false;
    public int seekingFusionTicks = 2400;
    protected static final EntityDataAccessor<Byte> DATA_WIDTH = SynchedEntityData.defineId(BaubleEntity.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_HEIGHT = SynchedEntityData.defineId(BaubleEntity.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_DEPTH = SynchedEntityData.defineId(BaubleEntity.class, EntityDataSerializers.BYTE);
    public int getHeight() {return this.entityData.get(DATA_HEIGHT);}
    public void setHeight(byte height) {this.entityData.set(DATA_HEIGHT, height);}
    public int getWidth() {return this.entityData.get(DATA_WIDTH);}
    public void setWidth(byte width) {this.entityData.set(DATA_WIDTH, width);}
    public int getDepth() {return this.entityData.get(DATA_DEPTH);}
    public void setDepth(byte depth) {this.entityData.set(DATA_DEPTH, depth);}

    public static final int[][] SILHOUETTES = {
            {4, 4, 4},
            {4, 12, 4},
            {8, 12, 4},
            {8, 8, 8}
    };
    public BaubleEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2);
    }
    @Override
    public boolean prefersLowStep() {
        return this.usesOrientedCollision();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_WIDTH, (byte) 1);
        entityData.define(DATA_HEIGHT, (byte) 1);
        entityData.define(DATA_DEPTH, (byte) 1);
    }
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("Width", (byte) this.getWidth());
        compound.putByte("Height", (byte) this.getHeight());
        compound.putByte("Depth", (byte) this.getDepth());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("Height")) this.setHeight(compound.getByte("Height"));
        if (compound.contains("Width")) this.setWidth(compound.getByte("Width"));
        if (compound.contains("Depth")) this.setDepth(compound.getByte("Depth"));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_DEPTH.equals(key) || DATA_HEIGHT.equals(key) || DATA_WIDTH.equals(key)) {
            this.applyShape();
        }
    }

    @Override
    protected VoxelShape generateShape(final RandomSource random, final boolean entropic) {
        if (getWidth() == (byte) 1 && getDepth() == (byte) 1 && getHeight() == (byte) 1) {
            int[] size = SILHOUETTES[random.nextInt(SILHOUETTES.length)];
            double w = size[0] / 16.0;
            double h = size[1] / 16.0;
            double d = size[2] / 16.0;

            setWidth((byte) (w*16));
            setHeight((byte) (h*16));
            setDepth((byte) (d*16));

            return Shapes.box(0.0, 0.0, 0.0, w, h, d);
        }
        return Shapes.box(0.0, 0.0, 0.0, getWidth()/16f, getHeight()/16f, getDepth()/16f);
    }

    public static boolean checkSpawnRules(EntityType<BaubleEntity> entityType, ServerLevelAccessor serverLevelAccessor, MobSpawnType mobSpawnType, BlockPos blockPos, RandomSource randomSource) {
        return serverLevelAccessor.getBlockState(blockPos).isAir() && !serverLevelAccessor.getBlockState(blockPos.below()).isAir();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity()!= null && source.getEntity() instanceof Player)
            baubleHurt();
        return super.hurt(source, amount);
    }

    protected void baubleHurt() {
        seekingFusion = true;
        AABB detectionBox = this.makeBoundingBox().inflate(20);
        List<BaubleEntity> entities = level().getEntitiesOfClass(BaubleEntity.class, detectionBox);

        for (BaubleEntity bauble : entities) {
            if (bauble == this) continue;
            if (bauble instanceof TrinketEntity) continue;
            bauble.setMovementTarget(Target.followingEntity(this, 0));
        }
    }

    @Override
    public float getVoicePitch() {
        return getContactPitchModifier();
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        return BeyondSoundEvents.PEARL_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return BeyondSoundEvents.PEARL_DEATH.get();
    }

    @Override
    protected float getContactPitchModifier() {
        return 2 - ((getDepth() + getHeight() + getWidth()) / 3f) / 8f;
    }

    @Override
    public void tick() {
        super.tick();
        baubleTick();
    }

    protected void baubleTick() {
        if (seekingFusion && !level().isClientSide && seekingFusionTicks > 0) {
            seekingFusionTicks--;
            if (seekingFusionTicks == 0) {
                seekingFusionTicks = 2400;
                seekingFusion = false;
            }
            attemptFuse();
        }
        if (this.random.nextBoolean() && tickCount % (level().isRaining() ? 200 : 2000) == 0) {
            Player player = level().getNearestPlayer(this, 16);
            if (player == null) return;
            this.setMovementTarget(Target.followingEntity(player, 2));
        }
        if (!seekingFusion && tickCount % 300 == 0) this.clearMovementTarget();
    }

    private void attemptFuse() {
        AABB detectionBox = this.getBoundingBox().inflate(0.5);
        List<BaubleEntity> entities = level().getEntitiesOfClass(BaubleEntity.class, detectionBox);
        entities.removeIf(e -> e.getType().equals(BeyondEntityTypes.TRINKET.get()));

        if (entities.size() > 5) {
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(new CloudColorTransitionOptions(
                        new Vector3f(0.9f, 0.9f, 0.9f),
                        new Vector3f(1, 1, 1),
                        2.5f
                ), this.getX(), this.getY(), this.getZ(), 10, 0.1, 0.3, 0.1, 0.03);
                serverLevel.sendParticles(BeyondParticleTypes.WIND.get(), this.getX(), this.getY()+1, this.getZ(), 10, 0, 1, 0, 0.1);
            }

            level().playSound(this, this.getOnPos(), BeyondSoundEvents.PEARL_CLINK.get(), SoundSource.AMBIENT, 1, 1);
            SiblingEntity sibling = new SiblingEntity(BeyondEntityTypes.SIBLING.get(), level());
            sibling.setPos(this.position());
            sibling.setBirth(true);
            level().addFreshEntity(sibling);
            level().broadcastEntityEvent(sibling, SiblingEntity.BIRTH);
            for (BaubleEntity bauble : entities) {
                bauble.discard();
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult bauble = baubleInteract(player, hand);
        if (bauble != InteractionResult.PASS) return bauble;
        return super.mobInteract(player, hand);
    }

    protected InteractionResult baubleInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();

        if (item instanceof DyeItem) {

            if (player instanceof ServerPlayer serverPlayer)
                if (serverPlayer.level().getServer() != null) {
                    ResourceLocation recipeAdvancementId = ResourceLocation.fromNamespaceAndPath("the_beyond", "recipes/tools/ocarina");
                    AdvancementHolder holder = serverPlayer.server.getAdvancements().get(recipeAdvancementId);

                    if (holder != null) {
                        AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(holder);
                        for (String criterion : progress.getRemainingCriteria()) {
                            serverPlayer.getAdvancements().award(holder, criterion);
                        }
                    }

                    BeyondCriteriaTriggers.TAME_BAUBLE.get().trigger(serverPlayer);
                }

            itemstack.consume(1, player);
            tame(item, player);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    public void tame(Item item, Player player) {
        DyeItem dyeitem = (DyeItem)item;
        DyeColor dyecolor = dyeitem.getDyeColor();
        int col = dyeitem.getDyeColor().getTextureDiffuseColor();

        float r = ((col >> 16) & 0xFF) / 255f;
        float g = ((col >> 8) & 0xFF) / 255f;
        float b = (col & 0xFF) / 255f;

        TrinketEntity trinket = new TrinketEntity(BeyondEntityTypes.TRINKET.get(), level());
        trinket.entityData.set(DATA_SHAPE_SEED, this.entityData.get(DATA_SHAPE_SEED));
        trinket.setDepth((byte) this.getDepth());
        trinket.setWidth((byte) this.getWidth());
        trinket.setHeight((byte) this.getHeight());
        trinket.setVariant(TrinketEntity.VARIANTS[random.nextInt(TrinketEntity.VARIANTS.length)]);
        trinket.setBodyColor(dyecolor.getTextureDiffuseColor());
        trinket.setDyeColor(dyecolor);
        trinket.setPos(this.position());
        trinket.rotation.set(this.rotation);
        trinket.setOwner(player.getUUID());
        level().addFreshEntity(trinket);

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new SmokeColorTransitionOptions(
                    new Vector3f(r, g, b),
                    new Vector3f(r + 0.1f, g + 0.1f, b + 0.1f),
                    2.2f
            ), this.getX(), this.getY(), this.getZ(), 3, 0.2, 0.2, 0.2, 0.01);

            serverLevel.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.5, this.getZ(), 3, 0.1, 0.1, 0.1, 0.03);
        }

        this.discard();
    }
}
