package com.thebeyond.mixin.compat.phantasm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code DragonMintBlock.randomTick} reads {@code has_fruit} unguarded; cancel when the
 *  state's block isn't dragon_mint to dodge {@code IllegalArgumentException} crashes. */
@Pseudo
@Mixin(targets = "net.lyof.phantasm.block.custom.DragonMintBlock")
public abstract class DragonMintBlockMixin {
    private static final ResourceLocation DRAGON_MINT_ID =
            ResourceLocation.fromNamespaceAndPath("phantasm", "dragon_mint");

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void the_beyond$guardForeignState(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
            CallbackInfo ci) {
        if (!DRAGON_MINT_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            ci.cancel();
        }
    }
}
