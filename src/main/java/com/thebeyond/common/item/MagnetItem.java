package com.thebeyond.common.item;

import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

public class MagnetItem extends Item {
    public final double range;

    public MagnetItem(Properties properties, double range) {
        super(properties);
        this.range = range;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);

        if (!level.isClientSide) {
            double range = 32;
            Vec3 eyePos = player.getEyePosition();
            Vec3 endPos = eyePos.add(player.getLookAngle().scale(range));

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
                BlockState state = level.getBlockState(pos);

                if (state.is(BeyondTags.METAL_BLOCKS)) {

                    Vec3 playerPos = player.position();
                    Vec3 blockCenter = Vec3.atCenterOf(pos);
                    Vec3 direction = blockCenter.subtract(playerPos).normalize();

                    player.setDeltaMovement(direction.scale(1.5));
                    player.hurtMarked = true;

                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(BeyondParticleTypes.GLOP.get(), blockCenter.x, blockCenter.y, blockCenter.z, 20, 0.5, 0.5, 0.5, 0.1);

                        for (int i = 0; i < 15; i++) {
                            double lerp = i / 15.0;
                            Vec3 particlePos = playerPos.lerp(blockCenter, lerp);
                            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, particlePos.x, particlePos.y + (level.random.nextGaussian()), particlePos.z, 2, 0.1, 0.1, 0.1, 0);
                        }
                    }

                    player.awardStat(Stats.ITEM_USED.get(this));
                    player.getCooldowns().addCooldown(this, 10);
                    return InteractionResultHolder.success(itemstack);
                } else {
                    level.playLocalSound(player, SoundEvents.VAULT_DEACTIVATE, SoundSource.PLAYERS, 1, 1);
                }
            } else {
                level.playLocalSound(player, SoundEvents.VAULT_DEACTIVATE, SoundSource.PLAYERS, 1, 1);
            }
        }

        return InteractionResultHolder.pass(itemstack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        double halfRange = range/2;
        level.getEntitiesOfClass(ItemEntity.class, new AABB(entity.position().subtract(halfRange, halfRange, halfRange), entity.position().add(halfRange, halfRange, halfRange)))
                .forEach(itemEntity -> {
                            if (!itemEntity.hasPickUpDelay()) {
                                double factor = 0.01 * range / itemEntity.position().vectorTo(entity.position()).length();
                                itemEntity.addDeltaMovement(itemEntity.position().vectorTo(entity.position()).multiply(factor, factor, factor));
                            }
                        }
                );

    }
}
