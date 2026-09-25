package com.thebeyond.common.item;

import com.thebeyond.client.gui.OcarinaOverlay;
import com.thebeyond.common.entity.TrinketEntity;
import com.thebeyond.common.entity.util.livingblock.movement.Target;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.util.OcarinaMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
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
    private static final int MODE_SWITCH_TICKS = 20;

    private final List<TrinketEntity> linkedTrinkets = new ArrayList<>();

    public List<TrinketEntity> getLinkedTrinkets() {
        return linkedTrinkets;
    }

    public void clearLinkedTrinkets() {
        linkedTrinkets.clear();
    }

    public OcarinaItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!stack.has(BeyondComponents.OCARINA_MODE)) stack.set(BeyondComponents.OCARINA_MODE, OcarinaMode.toInt(OcarinaMode.SELECT));
        if (isSelected && entity.tickCount%20==0 && !linkedTrinkets.isEmpty()) {
            linkedTrinkets.removeIf(entity1 -> !entity1.isAlive());
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        if (getUseDuration(stack, livingEntity) - timeCharged < 20) {
            if (livingEntity instanceof Player player)
                doUse(level, player, stack);
        }
        super.releaseUsing(stack, level, livingEntity, timeCharged);
    }

    private void doUse(Level level, Player player, ItemStack stack) {
        OcarinaMode mode = getMode(stack);
        if (mode == OcarinaMode.SELECT) {
            select(level, player);
            return;
        }
        if (linkedTrinkets.isEmpty()) {
            player.displayClientMessage(Component.translatable("screen.the_beyond.ocarina.no_trinkets"), true);
            return;
        }
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

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        int elapsed = getUseDuration(stack, livingEntity) - remainingUseDuration;
        if (livingEntity instanceof Player player)
            if (elapsed > 0 && elapsed % 15 == 0) {
                OcarinaMode current = getMode(stack);
                OcarinaMode next = current.next();
                setMode(stack, next);

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
            for (TrinketEntity trinket : linkedTrinkets) {
                if (!trinket.isAlive()) continue;
                trinket.setSelected(false);
            }
        }
        linkedTrinkets.clear();
        for (TrinketEntity trinket : nearby) {
            if (isOwnedBy(trinket, player)) {
                linkedTrinkets.add(trinket);
                trinket.setSelected(true);
            }
        }

        player.displayClientMessage(Component.translatable("screen.the_beyond.ocarina.trinkets_selected", linkedTrinkets.size()), true);
    }

    private void follow(Player player) {
        for (TrinketEntity trinket : linkedTrinkets) {
            trinket.setMovementTarget(Target.followingEntity(player, 3));
        }
    }

    private void guide(Level level, Player player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 endPos = eyePos.add(player.getLookAngle().scale(64));

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

                for (TrinketEntity trinket : linkedTrinkets) {
                    trinket.setMovementTarget(Target.near(centre, CALL_RADIUS));
                }
            }
        }
    }

    private void scatter(Level level, Player player) {

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
}
