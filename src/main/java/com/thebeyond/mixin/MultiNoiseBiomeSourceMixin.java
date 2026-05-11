package com.thebeyond.mixin;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** Guards {@code getNoiseBiome} against ResourceKey contamination in the parameter list:
 *  the implicit checkcast to Holder crashes pre-@Inject. {@code @Overwrite} is required
 *  because the failure happens in bytecode, not a wrappable call. */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {

    @Shadow @Final
    private Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;

    @Shadow
    public abstract Climate.ParameterList<Holder<Biome>> parameters();

    /**
     * @author TheBeyond
     * @reason Prevent ClassCastException when parameter list contains ResourceKey instead of Holder
     */
    @Overwrite
    public Holder<Biome> getNoiseBiome(Climate.TargetPoint targetPoint) {
        Object result = this.parameters().findValue(targetPoint);

        if (result instanceof Holder<?>) {
            return (Holder<Biome>) result;
        }

        // Contaminated: find first valid Holder as fallback
        Holder<Biome> firstValid = null;
        for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : this.parameters().values()) {
            Object val = (Object) pair.getSecond();
            if (val instanceof Holder<?>) {
                firstValid = (Holder<Biome>) val;
                break;
            }
        }

        // Total contamination should never happen in practice (at least one entry is
        // always a valid Holder), but returning the first pair's value raw is safer
        // than null — vanilla code never expects null from getNoiseBiome().
        return firstValid != null ? firstValid : (Holder<Biome>) (Object) this.parameters().values().getFirst().getSecond();
    }
}
