package com.thebeyond.common.item;

import com.thebeyond.common.entity.PearlItemEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

public class PearlItem  extends Item implements ProjectileItem {
    public PearlItem(Properties properties) {
        super(properties);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            PearlItemEntity pearl = new PearlItemEntity(BeyondEntityTypes.PEARL_BEAD.get(), level);

            pearl.setItem(itemstack);
            pearl.setPos(player.getX(), player.getY()+1, player.getZ());
            pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(pearl);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        PearlItemEntity pearl = new PearlItemEntity(BeyondEntityTypes.PEARL_BEAD.get(), level);
        return pearl;
    }

    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
                .uncertainty(DispenseConfig.DEFAULT.uncertainty() * 0.5F)
                .power(DispenseConfig.DEFAULT.power() * 1.25F)
                .build();
    }
}
