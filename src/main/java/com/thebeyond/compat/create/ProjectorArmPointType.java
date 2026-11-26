package com.thebeyond.compat.create;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Lets Create's mechanical arm pick the projector as a target, then fill and empty it through its item handler. */
public final class ProjectorArmPointType extends ArmInteractionPointType {

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterEvent event) -> {
            try {
                event.register(CreateRegistries.ARM_INTERACTION_POINT_TYPE,
                        ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "projector"), ProjectorArmPointType::new);
            } catch (Throwable t) {
                TheBeyond.LOGGER.error("[TheBeyond] Failed to register the projector as a Create arm target", t);
            }
        });
    }

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return state.is(BeyondBlocks.PROJECTOR.get());
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new ArmInteractionPoint(this, level, pos, state);
    }
}
