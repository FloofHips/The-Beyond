package com.thebeyond.mixin.compat.simulated;

import com.thebeyond.BeyondConfig;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lowers Simulated's disassembly floor with a sea below the floor (negative VOID_SEA_OFFSET), never above vanilla. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity", remap = false)
public abstract class SimContraptionFloorMixin {

    // The disassembly check is `bb.minY() < level.getMinBuildHeight()`, so that floor drops to the lowered sea's Y.
    @Redirect(
            method = "throwDisassemblyExceptions",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"),
            remap = false, require = 0)
    private int the_beyond$lowerContraptionFloor(Level level) {
        int minY = level.getMinBuildHeight();
        int offset = BeyondConfig.VOID_SEA_OFFSET.get();
        if (offset >= 0 || level.dimension() != Level.END) return minY;
        return minY + offset - 16; // sea Y minus a draft buffer so a floating contraption's hull can't dip into the floor
    }
}
