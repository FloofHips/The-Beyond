package com.thebeyond.mixin;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryFileCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Intercepts the erased {@code encode(Object)} bridge before its checkcast to Holder so
 *  ResourceKey contamination (via generic erasure from other mods) doesn't crash saves. */
@Mixin(RegistryFileCodec.class)
public class RegistryFileCodecMixin<E> {

    @SuppressWarnings({"unchecked", "rawtypes", "target"})
    @Inject(
            method = "encode(Ljava/lang/Object;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <T> void the_beyond$safeBridgeEncode(Object input, DynamicOps<T> ops, Object prefix, CallbackInfoReturnable<DataResult<T>> cir) {
        if (input instanceof ResourceKey<?> key) {
            cir.setReturnValue((DataResult<T>) (DataResult) ResourceLocation.CODEC.encode(key.location(), ops, (T) prefix));
        } else if (input != null && !(input instanceof Holder<?>)) {
            cir.setReturnValue(DataResult.error(() -> "[TheBeyond] Non-Holder in registry encode: " + input.getClass().getName()));
        }
    }
}
