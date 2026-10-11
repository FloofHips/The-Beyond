package com.thebeyond.mixin.client;

import com.thebeyond.client.particle.CircleColorTransitionOptions;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void beyond$alertParticles(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        if (stack.isEmpty()) return;
        if (!stack.has(BeyondComponents.ALERT.get())) return;

        if (self.level().isClientSide && self.tickCount % 20 == 0) {
            self.level().addParticle(
                    new CircleColorTransitionOptions(
                            new Vector3f(0.3f, 0.6f, 0.8f),
                            new Vector3f(1.0f, 1.0f, 1.0f),
                            0.5f
                    ), self.getX(), self.getY() + self.getBbHeight() + 0.2, self.getZ(), 0, 0, 0
            );
        }
    }
}