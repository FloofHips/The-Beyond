package com.thebeyond.common.fluids;

import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

public class GellidVoid extends BaseFlowingFluid {
    protected GellidVoid(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isSource(FluidState fluidState) {
        return false;
    }

    @Override
    public int getAmount(FluidState fluidState) {
        return 0;
    }

}