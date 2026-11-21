package com.thebeyond.common.item;

import com.thebeyond.common.entity.PearlItemEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

public class PearlItem  extends Item implements ProjectileItem {
    public static final float THROW_PITCH_OFFSET = -20.0F;
    public static final float THROW_VELOCITY = 0.5F;
    public static final float THROW_INACCURACY = 1.0F;

    public PearlItem(Properties properties) {
        super(properties);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        level.playSound((Player)null, player.getX(), player.getY(), player.getZ(), BeyondSoundEvents.ITEM_THROW.get(), SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!level.isClientSide) {
            PearlItemEntity pearl = new PearlItemEntity(BeyondEntityTypes.PEARL_BEAD.get(), level);

            pearl.setItem(itemstack);
            pearl.setPos(player.getX(), player.getY()+1, player.getZ());
            pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), THROW_PITCH_OFFSET, THROW_VELOCITY, THROW_INACCURACY);
            level.addFreshEntity(pearl);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        PearlItemEntity pearl = new PearlItemEntity(BeyondEntityTypes.PEARL_BEAD.get(), level);
        pearl.setItem(stack);
        pearl.setPos(pos.x(), pos.y(), pos.z());
        return pearl;
    }

    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
                .uncertainty(THROW_INACCURACY)
                .power(THROW_VELOCITY)
                .build();
    }

    // Matches a level throw by a player: shootFromRotation adds -sin(pitch offset) to y before normalizing.
    @Override
    public void shoot(Projectile projectile, double x, double y, double z, float velocity, float inaccuracy) {
        double lift = y == 0 ? -Mth.sin(THROW_PITCH_OFFSET * Mth.DEG_TO_RAD) : 0;
        projectile.shoot(x, y + lift, z, velocity, inaccuracy);
    }
}
