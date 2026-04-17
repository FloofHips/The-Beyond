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

/**
 * Fixes ClassCastException in MultiNoiseBiomeSource.getNoiseBiome() caused by
 * ResourceKey contamination in the climate parameter list.
 *
 * The vanilla method returns this.parameters().findValue(target) which does an
 * implicit checkcast to Holder. If the stored value is a ResourceKey (from mod
 * contamination via generic erasure), the cast crashes at the JVM bytecode level
 * — before any @Inject at RETURN could fire.
 *
 * @Overwrite is necessary here because the crash occurs in the implicit cast,
 * not in a method call that can be wrapped.
 */
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
