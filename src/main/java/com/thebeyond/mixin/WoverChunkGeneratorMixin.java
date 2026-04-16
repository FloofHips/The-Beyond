package com.thebeyond.mixin;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.BeyondEndBiomeSource;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents Wover/BetterX's {@code enforceGeneratorInWorldGenSettings} from replacing Beyond's
 * chunk generator with a {@code WoverChunkGenerator} at dimension-load time. Without this,
 * Beyond's End is silently swapped out because {@code BeyondEndBiomeSource} isn't a Wover type.
 *
 * <p>Only cancels for {@code LevelStem.END} + {@link BeyondEndBiomeSource}. Soft-targeted
 * via {@code @Pseudo} — silent no-op without WorldWeaver.</p>
 */
@Pseudo
@Mixin(targets = "org.betterx.wover.generator.impl.chunkgenerator.WoverChunkGenerator", remap = false)
public class WoverChunkGeneratorMixin {

    @Inject(
            method = "enforceGeneratorInWorldGenSettings",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void the_beyond$preserveBeyondEnd(
            RegistryAccess access,
            ResourceKey<LevelStem> dimensionKey,
            ResourceKey<DimensionType> dimensionTypeKey,
            ChunkGenerator loadedChunkGenerator,
            Registry<LevelStem> dimensionRegistry,
            CallbackInfoReturnable<Registry<LevelStem>> cir) {

        if (!LevelStem.END.equals(dimensionKey)) {
            return;
        }
        if (loadedChunkGenerator == null
                || !(loadedChunkGenerator.getBiomeSource() instanceof BeyondEndBiomeSource)) {
            return;
        }

        TheBeyond.LOGGER.info(
                "[TheBeyond] Beyond owns minecraft:the_end — vetoing WoverChunkGenerator.enforceGeneratorInWorldGenSettings replacement.");
        cir.setReturnValue(dimensionRegistry);
    }
}
