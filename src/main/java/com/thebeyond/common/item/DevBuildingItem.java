package com.thebeyond.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public abstract class DevBuildingItem extends Item {
    public DevBuildingItem(Properties properties) {
        super(properties);
    }

    protected BlockState getPlacementBlock(Player player) {
        ItemStack offhand = player.getOffhandItem();

        if (offhand.isEmpty()) {
            return Blocks.STONE.defaultBlockState();
        } else if (offhand.getItem() == Blocks.BARRIER.asItem()) {
            return Blocks.AIR.defaultBlockState();
        } else if (offhand.getItem() instanceof BlockItem) {
            return ((BlockItem) offhand.getItem()).getBlock().defaultBlockState();
        }

        return Blocks.STONE.defaultBlockState();
    }

    protected void playPlaceSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    protected void playErrorSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.5F, 0.5F);
    }

    protected void spawnParticles(Level level, BlockPos pos) {
        if (level.isClientSide) {
            for(int i = 0; i < 5; ++i) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        pos.getX() + level.random.nextDouble(),
                        pos.getY() + level.random.nextDouble(),
                        pos.getZ() + level.random.nextDouble(),
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    protected void storePosition(ItemStack stack, String key, BlockPos pos) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        tag.putLong(key, pos.asLong());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    protected BlockPos getStoredPosition(ItemStack stack, String key) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            return null;
        }
        CompoundTag tag = Objects.requireNonNull(stack.get(DataComponents.CUSTOM_DATA)).copyTag();
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    protected void clearPosition(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(key)) {
            CompoundTag tag = data.copyTag();
            tag.remove(key);
            if (tag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        }
    }
}
