package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.AuroraciteBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

@EventBusSubscriber(modid = TheBeyond.MODID)
public class BeyondEntityEvents {

    @SubscribeEvent
    public static void onChorusFruitTeleport(EntityTeleportEvent.ChorusFruit event) {
        Entity entity = event.getEntity();
        if (AuroraciteBlock.canEntityWalkOn(entity)) return;

        Level level = entity.level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
                event.getTargetX(), event.getTargetY(), event.getTargetZ());
        int min = level.getMinBuildHeight();

        while (cursor.getY() > min) {
            BlockState below = level.getBlockState(cursor.below());
            if (below.blocksMotion()) {
                if (below.is(BeyondBlocks.AURORACITE.get())) {
                    event.setCanceled(true);
                }
                return;
            }
            cursor.move(0, -1, 0);
        }
    }
}
