package com.thebeyond.common.item;

import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class NoFluidMobBucketItem extends MobBucketItem {
    public NoFluidMobBucketItem(EntityType<?> type, Fluid content, SoundEvent emptySound, Properties properties) {
        super(type, content, emptySound, properties);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        BlockHitResult blockhitresult = getPlayerPOVHitResult(level, player, this.content == Fluids.EMPTY ? net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY : net.minecraft.world.level.ClipContext.Fluid.NONE);
        if (blockhitresult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        } else if (blockhitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        } else {


            BlockPos blockpos = blockhitresult.getBlockPos();
            Direction direction = blockhitresult.getDirection();
            BlockPos blockpos1 = blockpos.relative(direction);

            if (level.mayInteract(player, blockpos) && player.mayUseItemAt(blockpos1, direction, itemstack)) {

                    if (level instanceof ServerLevel) {
                        this.spawn((ServerLevel)level, itemstack, blockpos1);
                        level.gameEvent(player, GameEvent.ENTITY_PLACE, blockpos1);
                    }

                    player.awardStat(Stats.ITEM_USED.get(this));
                    ItemStack itemstack1 = ItemUtils.createFilledResult(itemstack, player, getEmptySuccessItem(itemstack, player));
                    return InteractionResultHolder.sidedSuccess(itemstack1, level.isClientSide());

            } else {
                return InteractionResultHolder.fail(itemstack);
            }
        }
    }

    private void spawn(ServerLevel serverLevel, ItemStack bucketedMobStack, BlockPos pos) {
        Entity var5 = BeyondEntityTypes.BEAD.get().spawn(serverLevel, bucketedMobStack, (Player)null, pos, MobSpawnType.BUCKET, true, false);
        if (var5 instanceof Bucketable bucketable) {
            CustomData customdata = (CustomData)bucketedMobStack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
            bucketable.loadFromBucketTag(customdata.copyTag());
            bucketable.setFromBucket(true);
        }

    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customdata = (CustomData)stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
        if (customdata.isEmpty()) {
            return;
        }
        int bodyColor = -1;
        int dyeColor = -1;
        int size = 0;

        if (customdata.contains("BodyColor")) bodyColor = customdata.copyTag().getInt("BodyColor");
        if (customdata.contains("DyeColor")) dyeColor = customdata.copyTag().getInt("DyeColor");
        if (customdata.contains("Width") && customdata.contains("Height") && customdata.contains("Depth")) size = customdata.copyTag().getInt("Width") + customdata.copyTag().getInt("Height") + customdata.copyTag().getInt("Depth");

        DyeColor dyeColor1 = DyeColor.byId(dyeColor);

        tooltipComponents.add(intToComponent(size));
        tooltipComponents.add(Component.translatable("item.color", new Object[]{String.format(Locale.ROOT, "#%06X", bodyColor)}).withStyle(ChatFormatting.GRAY).withColor(bodyColor));
        tooltipComponents.add(Component.translatable("trinket.growth_color", dyeColor1.getName()).withStyle(ChatFormatting.GRAY).withColor(dyeColor1.getTextColor()));
    }

    public Component intToComponent(int size) {
        if (size < 24) return Component.translatable("trinket.small").withStyle(ChatFormatting.GRAY);
        if (size < 34) return Component.translatable("trinket.medium").withStyle(ChatFormatting.GRAY);
        if (size > 34) return Component.translatable("trinket.large").withStyle(ChatFormatting.GRAY);
        return Component.literal("...").withStyle(ChatFormatting.GRAY);
    }
}
