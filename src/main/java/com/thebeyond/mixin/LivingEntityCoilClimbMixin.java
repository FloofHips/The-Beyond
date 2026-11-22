package com.thebeyond.mixin;

import com.thebeyond.common.block.CoilVertebraeBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockFrames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCoilClimbMixin {

    // the 2-pixel pole lets an angled push slide the player off, so pushing into it within 60 degrees aims at its axis
    @Inject(method = "handleOnClimbable", at = @At("RETURN"), cancellable = true)
    private void the_beyond$holdCoilPole(Vec3 deltaMovement, CallbackInfoReturnable<Vec3> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player) || !self.horizontalCollision || !self.onClimbable()) {
            return;
        }
        BlockPos pos = self.getLastClimbablePos().orElse(null);
        if (pos == null) {
            return;
        }
        BlockState state = self.level().getBlockState(pos);
        if (!(state.getBlock() instanceof CoilVertebraeBlock) || state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
            return;
        }
        Vec3 out = cir.getReturnValue();
        Vec3 axis = LivingBlockFrames.toWorld(self.level(), Vec3.atCenterOf(pos));
        double dx = axis.x - self.getX();
        double dz = axis.z - self.getZ();
        double reach = Math.sqrt(dx * dx + dz * dz);
        double speed = Math.sqrt(out.x * out.x + out.z * out.z);
        if (reach < 1.0E-4 || speed < 1.0E-6 || out.x * dx + out.z * dz <= 0.5 * reach * speed) {
            return;
        }
        cir.setReturnValue(new Vec3(dx / reach * speed, out.y, dz / reach * speed));
    }
}
