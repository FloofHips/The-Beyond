package com.thebeyond.common.item;

import com.thebeyond.common.entity.GravistarEntity;
import com.thebeyond.common.entity.SmokeFuseEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

public class SmokeFuseItem extends Item implements ProjectileItem {
    public SmokeFuseItem(Properties properties) {
        super(properties);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            itemstack.set(DataComponents.DYED_COLOR, new DyedItemColor(level.random.nextInt(48665565), true));
            return InteractionResultHolder.success(itemstack);
        }

        if (!level.isClientSide) {
            SmokeFuseEntity fuse = new SmokeFuseEntity(BeyondEntityTypes.SMOKE_FUSE.get(), level);
            fuse.setItem(itemstack);

            fuse.setPos(player.getX(), player.getY()+1, player.getZ());
            fuse.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(fuse);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        SmokeFuseEntity fuse = new SmokeFuseEntity(BeyondEntityTypes.SMOKE_FUSE.get(), level);
        return fuse;
    }

    public DispenseConfig createDispenseConfig() {
        return DispenseConfig.builder().uncertainty(DispenseConfig.DEFAULT.uncertainty() * 0.5F).power(DispenseConfig.DEFAULT.power() * 1.25F).build();
    }
}
