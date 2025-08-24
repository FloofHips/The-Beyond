package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.blocks.VoidFlameBlock;
import com.thebeyond.common.fluids.GellidVoidBlock;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import com.thebeyond.common.fluids.GellidVoid;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.*;

import java.util.function.Supplier;

public class BeyondFluids {
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, TheBeyond.MODID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, TheBeyond.MODID);

    public static final DeferredHolder<FluidType, FluidType> GELLID_VOID_TYPE = FLUID_TYPES.register("gellid_void", () -> new FluidType(FluidType.Properties.create()
          .supportsBoating(true)
          .canHydrate(true)));
    public static final DeferredHolder<Fluid, Fluid> GELLID_VOID = FLUIDS.register("gellid_void", () -> new BaseFlowingFluid.Source(AcidFluidProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> GELLID_VOID_FLOWING = FLUIDS.register("gellid_void_flowing", () -> new BaseFlowingFluid.Flowing(AcidFluidProperties()));

    public static BaseFlowingFluid.Properties AcidFluidProperties() {
        return new GellidVoid.Properties(GELLID_VOID_TYPE, GELLID_VOID, GELLID_VOID_FLOWING)
                .block(BeyondBlocks.GELLID_VOID)
                .bucket(BeyondItems.GELLID_VOID_BUCKET);
    }

}
