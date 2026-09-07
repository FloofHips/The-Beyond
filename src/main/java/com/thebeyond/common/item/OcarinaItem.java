package com.thebeyond.common.item;

import com.thebeyond.common.entity.TrinketEntity;
import com.thebeyond.common.entity.util.livingblock.movement.Target;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

import static com.thebeyond.common.block.MemorFaucetBlock.AGE;

public class OcarinaItem extends Item {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final double CALL_RADIUS = 0.1;

    public OcarinaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);

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

                BlockPos a = player.getOnPos();
                AABB detectionBox = new AABB(a).inflate(8);

                Vec3 centre = pos.getCenter();
                List<TrinketEntity> entities = level.getEntitiesOfClass(TrinketEntity.class, detectionBox);
                for (TrinketEntity bead : entities) {
                    bead.setMovementTarget(Target.near(centre, CALL_RADIUS));
                }
                LOGGER.debug("[ocarina] call plan=emergent beads={} face={} target={} radius={}",
                        entities.size(), hit.getDirection(),
                        String.format("%.2f,%.2f,%.2f", centre.x, centre.y, centre.z),
                        CALL_RADIUS);
                return InteractionResultHolder.success(itemstack);
            }
        }
        return super.use(level, player, usedHand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        return super.finishUsingItem(stack, level, livingEntity);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return super.getUseDuration(stack, entity);
    }
}
