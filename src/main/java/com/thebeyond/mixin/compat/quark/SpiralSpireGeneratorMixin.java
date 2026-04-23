package com.thebeyond.mixin.compat.quark;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves Quark's Spiral Spire biome whitelist check from Y=256 down to the real
 * surface Y (the eventual END_STONE landing).
 *
 * <p>Beyond's {@code BeyondEndBiomeSource} is 3D: at Y=256 {@code getTerrainDensity}
 * is ~0, so {@code getNoiseBiome} always returns an entry from
 * {@code outerVoidBiomeList} — never attracta_expanse, peer_lands, or end_highlands.
 * Without this mixin the player cannot unlock spire spawning in Beyond by editing
 * the quark-common.toml, because the high-Y sample never matches.</p>
 *
 * <p>Quark is not on Beyond's compile classpath, so {@code SpiralSpiresModule.biomes}
 * and {@code .radius} are resolved reflectively and cached. Soft-targeted via
 * {@code @Pseudo} — no-op without Quark.</p>
 */
@Pseudo
@Mixin(targets = "org.violetmoon.quark.content.world.gen.SpiralSpireGenerator", remap = false)
public abstract class SpiralSpireGeneratorMixin {

    @Shadow(remap = false)
    public abstract void makeSpike(WorldGenRegion world, ChunkGenerator chunk, Random rand, BlockPos pos);

    // Cached reflective handles into org.violetmoon.quark.content.world.module.SpiralSpiresModule.
    private static Field the_beyond$biomesField;
    private static Field the_beyond$radiusField;
    private static Method the_beyond$canSpawnMethod;
    private static boolean the_beyond$reflectInit;
    private static boolean the_beyond$reflectOk;

    private static boolean the_beyond$initReflect() {
        if (the_beyond$reflectInit) return the_beyond$reflectOk;
        the_beyond$reflectInit = true;
        try {
            Class<?> module = Class.forName("org.violetmoon.quark.content.world.module.SpiralSpiresModule");
            the_beyond$biomesField = module.getField("biomes");
            the_beyond$radiusField = module.getField("radius");
            the_beyond$canSpawnMethod = the_beyond$biomesField.getType().getMethod("canSpawn", Holder.class);
            the_beyond$reflectOk = true;
        } catch (Throwable t) {
            the_beyond$reflectOk = false;
        }
        return the_beyond$reflectOk;
    }

    /**
     * Replaces {@code generateChunkPart} wholesale: same RNG consumption order as
     * Quark's original, but the biome check runs AFTER the downward END_STONE walk
     * instead of BEFORE, so Beyond's 3D biome source returns the real surface biome.
     */
    @Inject(
        method = "generateChunkPart(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/Random;Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/WorldGenRegion;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void the_beyond$sampleBiomeAtLandingY(
            BlockPos src, ChunkGenerator generator, Random random,
            BlockPos chunkCorner, WorldGenRegion world, CallbackInfo ci) {

        if (!the_beyond$initReflect()) return; // can't reach Quark — let original run

        int radius;
        Object biomesConfig;
        try {
            radius = the_beyond$radiusField.getInt(null);
            biomesConfig = the_beyond$biomesField.get(null);
        } catch (Throwable t) {
            return; // fall through to Quark's original
        }

        // Mirror Quark's distance guard verbatim (same RNG consumption).
        double dist = (chunkCorner.distSqr(src)) / ((16 * radius) * (16 * radius));
        if (dist > 0.5 && random.nextDouble() < (1.5F - dist)) {
            ci.cancel();
            return;
        }

        // 2 nextInt calls, in the same order Quark's original uses.
        BlockPos pos = chunkCorner.offset(random.nextInt(16), 256, random.nextInt(16));

        // Walk down to the real surface Y BEFORE the biome check.
        while (world.getBlockState(pos).getBlock() != Blocks.END_STONE) {
            pos = pos.below();
            if (pos.getY() < 10) {
                ci.cancel();
                return;
            }
        }

        // Sample biome at the actual landing. In Beyond this is a real surface biome
        // (attracta_expanse / peer_lands / end_highlands) — not an outer-void biome.
        Holder<Biome> biome = world.getBiomeManager().getBiome(pos);
        boolean ok;
        try {
            ok = (Boolean) the_beyond$canSpawnMethod.invoke(biomesConfig, biome);
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            ci.cancel();
            return;
        }

        makeSpike(world, generator, random, pos);
        ci.cancel(); // full method handled
    }
}
