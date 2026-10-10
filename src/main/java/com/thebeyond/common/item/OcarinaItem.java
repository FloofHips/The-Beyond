package com.thebeyond.common.item;

import com.thebeyond.client.gui.OcarinaOverlay;
import com.thebeyond.common.entity.TrinketEntity;
import com.thebeyond.common.entity.util.livingblock.movement.Target;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondSoundEvents;
import com.thebeyond.util.OcarinaMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.thebeyond.common.block.MemorFaucetBlock.AGE;

public class OcarinaItem extends Item {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final double CALL_RADIUS = 0.1;
    private static final double DETECTION_RADIUS = 8;
    private static final int MODE_SWITCH_TICKS = 12;

    private final List<TrinketEntity> linkedTrinkets = new ArrayList<>();

    public List<TrinketEntity> getLinkedTrinkets() {
        return linkedTrinkets;
    }

    public OcarinaItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!stack.has(BeyondComponents.OCARINA_MODE)) stack.set(BeyondComponents.OCARINA_MODE, OcarinaMode.toInt(OcarinaMode.SELECT));
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        if (getUseDuration(stack, livingEntity) - timeCharged < MODE_SWITCH_TICKS) {
            if (livingEntity instanceof Player player)
                doUse(level, player, stack);
        }
        super.releaseUsing(stack, level, livingEntity, timeCharged);
    }

    private void doUse(Level level, Player player, ItemStack stack) {
        if (player.isShiftKeyDown()) {
            level.playSound(player, player.blockPosition(), BeyondSoundEvents.OCARINA_DESELECT.get(), SoundSource.PLAYERS, 1f,1);
            if (!linkedTrinkets.isEmpty()) {
                player.displayClientMessage(Component.translatable("screen.the_beyond.ocarina.trinkets_deselected", linkedTrinkets.size()), true);
                deselectAll();
            }
            return;
        }

        OcarinaMode mode = getMode(stack);
        if (mode == OcarinaMode.SELECT) {
            select(level, player);
            player.getCooldowns().addCooldown(stack.getItem(), MODE_SWITCH_TICKS);
            return;
        }

        if (linkedTrinkets.isEmpty()) {
            player.displayClientMessage(Component.translatable("screen.the_beyond.ocarina.no_trinkets"), true);
            player.playSound(BeyondSoundEvents.OCARINA_FAIL.get(), 1f, 1+level.random.nextFloat());
            return;
        }

        player.playSound(getSoundEvent(mode), 1f,1+level.random.nextFloat()*0.1f);
        player.getCooldowns().addCooldown(stack.getItem(), MODE_SWITCH_TICKS);

        if (mode == OcarinaMode.GUIDE) {
            guide(level, player);
            return;
        }
        if (mode == OcarinaMode.FOLLOW) {
            follow(player);
            return;
        }
        if (mode == OcarinaMode.SCATTER) {
            scatter(level, player);
            return;
        }
    }

    private void deselectAll() {
        List<TrinketEntity> snapshot = new ArrayList<>(linkedTrinkets);
        for (TrinketEntity trinket : snapshot) {
            if (!trinket.isAlive()) continue;
            trinket.setSelected(false);
            trinket.clearMovementTarget();
        }
        linkedTrinkets.clear();
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        int elapsed = getUseDuration(stack, livingEntity) - remainingUseDuration;
        if (livingEntity instanceof Player player)
            if (elapsed > 0 && elapsed % MODE_SWITCH_TICKS == 0) {
                OcarinaMode current = getMode(stack);
                OcarinaMode next = current.next();
                setMode(stack, next);

                level.playSound(player, player.blockPosition(), BeyondSoundEvents.OCARINA_USE.get(), SoundSource.PLAYERS, 1f,1+level.random.nextFloat());
                if (level.isClientSide) {
                    player.displayClientMessage(next.displayName(), true);
                    OcarinaOverlay.alpha = 1;
                }
            }

        super.onUseTick(level, livingEntity, stack, remainingUseDuration);
    }

    public static OcarinaMode getMode(ItemStack stack) {
        if (!stack.has(BeyondComponents.OCARINA_MODE)) {
            stack.set(BeyondComponents.OCARINA_MODE, OcarinaMode.toInt(OcarinaMode.SELECT));
            return OcarinaMode.SELECT;
        }
        return OcarinaMode.fromInt(stack.get(BeyondComponents.OCARINA_MODE));
    }

    public static void setMode(ItemStack stack, OcarinaMode next) {
        stack.set(BeyondComponents.OCARINA_MODE, OcarinaMode.toInt(next));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        player.startUsingItem(usedHand);

        return InteractionResultHolder.consume(stack);
    }

    private void select(Level level, Player player) {
        AABB detectionBox = new AABB(player.getOnPos()).inflate(DETECTION_RADIUS);

        List<TrinketEntity> nearby = level.getEntitiesOfClass(TrinketEntity.class, detectionBox);

        if (!linkedTrinkets.isEmpty()) {
            deselectAll();
        }

        for (TrinketEntity trinket : nearby) {
            if (isOwnedBy(trinket, player)) {
                linkedTrinkets.add(trinket);
                trinket.setSelected(true);
            } else if (trinket.getOwnerUUID() == null) {
                trinket.setOwner(player.getUUID());
                linkedTrinkets.add(trinket);
                trinket.setSelected(true);
            }
        }

        player.displayClientMessage(Component.translatable("screen.the_beyond.ocarina.trinkets_selected", linkedTrinkets.size()), true);
        if (linkedTrinkets.isEmpty())
            player.playSound(BeyondSoundEvents.OCARINA_FAIL.get(), 1f,1+level.random.nextFloat());
        else {
            if (player instanceof ServerPlayer serverPlayer) {
                BeyondCriteriaTriggers.SELECT_TRINKET.get().trigger(serverPlayer);
            }
            player.playSound(getSoundEvent(OcarinaMode.SELECT), 1f, 1 + level.random.nextFloat() * 0.1f);
        }
    }

    private void follow(Player player) {
        List<TrinketEntity> snapshot = new ArrayList<>(linkedTrinkets);
        for (TrinketEntity trinket : snapshot) {
            trinket.setMovementTarget(Target.followingEntity(player, 3));
        }
    }

    private void guide(Level level, Player player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 endPos = eyePos.add(player.getLookAngle().scale(64));
        Vec3 forClient = null;

        if (!level.isClientSide) {
            ClipContext clipContext = new ClipContext(
                    eyePos,
                    endPos,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            );

            BlockHitResult hit = level.clip(clipContext);

            if (hit.getType() != HitResult.Type.MISS) {
                BlockPos pos = hit.getBlockPos();
                Vec3 centre = pos.getCenter();
                forClient = centre;

                List<TrinketEntity> snapshot = new ArrayList<>(linkedTrinkets);
                for (TrinketEntity trinket : snapshot) {
                    trinket.setMovementTarget(Target.near(centre, CALL_RADIUS));
                }
            }
            if (forClient!=null) {
                if (level instanceof ServerLevel serverLevel)
                    serverLevel.sendParticles((ServerPlayer) player, BeyondParticleTypes.ARROW.get(), true, forClient.x, forClient.y+1, forClient.z, 1, 0,0,0,  0);
            }
        }
    }

    private void scatter(Level level, Player player) {
        if (!linkedTrinkets.isEmpty()) {
            List<TrinketEntity> snapshot = new ArrayList<>(linkedTrinkets);
            for (TrinketEntity trinket : snapshot) {
                BlockPos pos = findScatterPos(level, player.getOnPos(), 8);
                if (pos == null) {
                    trinket.clearMovementTarget();
                    continue;
                }
                Vec3 centre = pos.getCenter();
                trinket.setMovementTarget(Target.near(centre, CALL_RADIUS));
            }
        }
        deselectAll();
    }

    private BlockPos findScatterPos(Level level, BlockPos origin, int radius) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        Iterable<BlockPos> pos = BlockPos.randomBetweenClosed(level.random,20,origin.getX()-radius, origin.getY()-1, origin.getZ()-radius, origin.getX()+1, origin.getY()+radius, origin.getZ()+radius);
            for (BlockPos position : pos) {
                if (position == null) continue;

                mutable.set(position);
                if (!level.isLoaded(mutable)) continue;
                if (!level.getBlockState(mutable).isAir()) continue;
                if (!level.getBlockState(mutable.below()).isSolid()) continue;

            return mutable.immutable();
        }

        return null;
    }

    private boolean isOwnedBy(TrinketEntity trinket, Player player) {
        return trinket.getOwnerUUID() != null && trinket.getOwnerUUID().equals(player.getUUID());
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        return super.finishUsingItem(stack, level, livingEntity);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    public SoundEvent getSoundEvent(OcarinaMode mode) {
        switch (mode) {
            case SELECT -> {
                return BeyondSoundEvents.OCARINA_SELECT.get();
            }
            case FOLLOW -> {
                return BeyondSoundEvents.OCARINA_FOLLOW.get();
            }
            case SCATTER -> {
                return BeyondSoundEvents.OCARINA_SCATTER.get();
            }
            case GUIDE -> {
                return BeyondSoundEvents.OCARINA_GUIDE.get();
            }
        }
        return null;
    }
}
