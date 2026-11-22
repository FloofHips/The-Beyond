package com.thebeyond.common.block;

import com.thebeyond.common.entity.PearlItemEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BedazzledEndStone extends Block {
    public BedazzledEndStone(Properties properties) {
        super(properties);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        Holder<Enchantment> silkTouch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        if (EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0) return;
        for (int i = 0; i < level.random.nextInt(1,5); i++) {
            PearlItemEntity pearl = new PearlItemEntity(BeyondEntityTypes.PEARL_BEAD.get(), level);
            pearl.setPos(pos.getX()+0.5f, pos.getY()+0.5f, pos.getZ()+0.5f);
            pearl.setDeltaMovement(new Vec3(1-level.random.nextFloat(), level.random.nextFloat(), 1-level.random.nextFloat()).scale(0.1f));
            level.addFreshEntity(pearl);
        }
    }
}
