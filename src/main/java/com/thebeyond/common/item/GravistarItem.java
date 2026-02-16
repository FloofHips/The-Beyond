package com.thebeyond.common.item;

import com.thebeyond.common.entity.GravistarEntity;
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

public class GravistarItem extends Item implements ProjectileItem {
    public GravistarItem(Properties properties) {
        super(properties);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            GravistarEntity star = new GravistarEntity(BeyondEntityTypes.GRAVISTAR.get(), level);

            star.setPos(player.getX(), player.getY()+1, player.getZ());
            star.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(star);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        GravistarEntity star = new GravistarEntity(BeyondEntityTypes.GRAVISTAR.get(), level);
        return star;
    }

    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return DispenseConfig.builder().uncertainty(DispenseConfig.DEFAULT.uncertainty() * 0.5F).power(DispenseConfig.DEFAULT.power() * 1.25F).build();
    }
}
