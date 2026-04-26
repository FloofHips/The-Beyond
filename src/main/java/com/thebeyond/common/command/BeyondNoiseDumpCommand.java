package com.thebeyond.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondTerrainParams;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /beyond_noise_dump} — samples {@link BeyondEndChunkGenerator#getTerrainDensity}
 * on a grid and writes a PNG to {@code <world>/beyond_noise_dumps/} for diagnosing
 * stretching/streaking at large {@code |X|}. Permission level 2.
 *
 * <p>Syntax: {@code /beyond_noise_dump [vertical] (here|at <pos>) [<size> [<stride>]] [normal|no_warp|no_wrap]}.
 * Horizontal is XZ at fixed Y; {@code vertical} is XY at fixed Z (exposes {@code cyclicDensity}
 * Y-periodicity invisible in horizontal dumps). Defaults: size=200, stride=2.
 * Modes {@code no_warp}/{@code no_wrap} progressively neutralize warp then wrap to
 * isolate the contribution of each transform.
 *
 * <p>Sampling and PNG write run via {@link CompletableFuture#runAsync}; the parameterized
 * {@code getTerrainDensity(x,y,z,params)} overload avoids swapping
 * {@link BeyondEndChunkGenerator#activeTerrainParams} (would race with chunk-gen workers).
 */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class BeyondNoiseDumpCommand {

    private BeyondNoiseDumpCommand() {}

    /** Default grid side in samples (200 -> 40 000 density evaluations). */
    private static final int DEFAULT_SIZE = 200;
    /** Default spacing in blocks between samples. */
    private static final int DEFAULT_STRIDE = 2;
    /** Distance-from-origin used when tinting "solid" cells; matches {@code TerrainDensityGridDumpTest}. */
    private static final float DISPLAY_DISTANCE_FROM_ORIGIN = 1500f;

    /** Subfolder under {@code <world>/} where dumps are written. */
    private static final String DUMP_SUBDIR = "beyond_noise_dumps";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Sampling plane orientation. {@code HORIZONTAL_XZ} is the XZ slice at fixed Y
     * (heightmap-style view); {@code VERTICAL_XY} is the XY slice at fixed Z (side
     * view — required to observe {@code cyclicDensity}'s Y-periodic modulation and
     * any divisor seams where {@code cycleHeight} crosses {@code y % cycleHeight}
     * discontinuities, both invisible in a horizontal dump).
     */
    private enum Plane {
        HORIZONTAL_XZ("xz"),
        VERTICAL_XY("xy");

        final String suffix;
        Plane(String suffix) { this.suffix = suffix; }
    }

    /** Diagnostic transform mode. Derives a {@link BeyondTerrainParams} from live params. */
    private enum Mode {
        /** Live params. */
        NORMAL,
        /** Zero warp amplitude, keep live wrap range. */
        NO_WARP,
        /** Zero warp and lift wrap range to {@link BeyondTerrainParams#MAX_WRAP_RANGE} (identity transform). */
        NO_WRAP;

        BeyondTerrainParams derive(BeyondTerrainParams live) {
            return switch (this) {
                case NORMAL  -> live;
                case NO_WARP -> new BeyondTerrainParams(live.wrapRange(), 0.0, live.warpScale());
                case NO_WRAP -> new BeyondTerrainParams(BeyondTerrainParams.MAX_WRAP_RANGE, 0.0, live.warpScale());
            };
        }

        String label() { return name().toLowerCase(); }
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Brigadier requires every terminal node to call .executes(...); the .then() chain
        // simulates optional args, producing a small but wide tree.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("beyond_noise_dump")
                .requires(src -> src.hasPermission(2));

        // --- here / at branches (horizontal XZ slice, default) ---
        root.then(buildHereBranch(Plane.HORIZONTAL_XZ));
        root.then(buildAtBranch(Plane.HORIZONTAL_XZ));

        // --- vertical XY slice (fixed Z) ---
        root.then(Commands.literal("vertical")
                .then(buildHereBranch(Plane.VERTICAL_XY))
                .then(buildAtBranch(Plane.VERTICAL_XY)));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildHereBranch(Plane plane) {
        return Commands.literal("here")
                .executes(ctx -> runHere(ctx, DEFAULT_SIZE, DEFAULT_STRIDE, Mode.NORMAL, plane))
                .then(Commands.argument("size", IntegerArgumentType.integer(4, 1024))
                        .executes(ctx -> runHere(ctx, IntegerArgumentType.getInteger(ctx, "size"), DEFAULT_STRIDE, Mode.NORMAL, plane))
                        .then(Commands.argument("stride", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> runHere(ctx,
                                        IntegerArgumentType.getInteger(ctx, "size"),
                                        IntegerArgumentType.getInteger(ctx, "stride"),
                                        Mode.NORMAL, plane))
                                .then(Commands.literal("normal").executes(ctx -> runHere(ctx,
                                        IntegerArgumentType.getInteger(ctx, "size"),
                                        IntegerArgumentType.getInteger(ctx, "stride"),
                                        Mode.NORMAL, plane)))
                                .then(Commands.literal("no_warp").executes(ctx -> runHere(ctx,
                                        IntegerArgumentType.getInteger(ctx, "size"),
                                        IntegerArgumentType.getInteger(ctx, "stride"),
                                        Mode.NO_WARP, plane)))
                                .then(Commands.literal("no_wrap").executes(ctx -> runHere(ctx,
                                        IntegerArgumentType.getInteger(ctx, "size"),
                                        IntegerArgumentType.getInteger(ctx, "stride"),
                                        Mode.NO_WRAP, plane)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAtBranch(Plane plane) {
        return Commands.literal("at")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> runAt(ctx, DEFAULT_SIZE, DEFAULT_STRIDE, Mode.NORMAL, plane))
                        .then(Commands.argument("size", IntegerArgumentType.integer(4, 1024))
                                .executes(ctx -> runAt(ctx, IntegerArgumentType.getInteger(ctx, "size"), DEFAULT_STRIDE, Mode.NORMAL, plane))
                                .then(Commands.argument("stride", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> runAt(ctx,
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                IntegerArgumentType.getInteger(ctx, "stride"),
                                                Mode.NORMAL, plane))
                                        .then(Commands.literal("normal").executes(ctx -> runAt(ctx,
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                IntegerArgumentType.getInteger(ctx, "stride"),
                                                Mode.NORMAL, plane)))
                                        .then(Commands.literal("no_warp").executes(ctx -> runAt(ctx,
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                IntegerArgumentType.getInteger(ctx, "stride"),
                                                Mode.NO_WARP, plane)))
                                        .then(Commands.literal("no_wrap").executes(ctx -> runAt(ctx,
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                IntegerArgumentType.getInteger(ctx, "stride"),
                                                Mode.NO_WRAP, plane))))));
    }

    // ------------------------------------------------------------------
    // Branch adapters
    // ------------------------------------------------------------------

    private static int runHere(CommandContext<CommandSourceStack> ctx, int size, int stride, Mode mode, Plane plane)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Entity entity = src.getEntity();
        if (entity == null) {
            src.sendFailure(Component.literal("/beyond_noise_dump here requires an entity source — use 'at <pos>' from the console."));
            return 0;
        }
        Vec3 pos = entity.position();
        return dispatch(src, (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z),
                size, stride, mode, plane);
    }

    private static int runAt(CommandContext<CommandSourceStack> ctx, int size, int stride, Mode mode, Plane plane)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        return dispatch(src, pos.getX(), pos.getY(), pos.getZ(), size, stride, mode, plane);
    }

    // ------------------------------------------------------------------
    // Dump core
    // ------------------------------------------------------------------

    private static int dispatch(CommandSourceStack src, int cx, int cy, int cz,
                                int size, int stride, Mode mode, Plane plane) {
        MinecraftServer server = src.getServer();

        // Resolve effective params on the main thread so the worker sees a single snapshot
        // even if a datapack reload happens mid-dump.
        BeyondTerrainParams liveParams = BeyondEndChunkGenerator.activeTerrainParams;
        BeyondTerrainParams effective = mode.derive(liveParams);

        File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        File dumpDir = new File(worldDir, DUMP_SUBDIR);
        if (!dumpDir.exists() && !dumpDir.mkdirs()) {
            src.sendFailure(Component.literal("Failed to create dump directory: " + dumpDir.getAbsolutePath()));
            return 0;
        }

        String modeLabel = mode.label();
        String fileName = String.format("%s_x%d_y%d_z%d_s%d_st%d_%s_%s.png",
                LocalDateTime.now().format(TS), cx, cy, cz, size, stride, modeLabel, plane.suffix);
        File outFile = new File(dumpDir, fileName);

        src.sendSuccess(() -> Component.literal(
                String.format("[beyond_noise_dump] Sampling %dx%d %s grid @ (%d, %d, %d) stride=%d mode=%s → %s",
                        size, size, plane.suffix, cx, cy, cz, stride, modeLabel, fileName))
                .withStyle(ChatFormatting.GRAY), false);

        CompletableFuture.runAsync(() -> {
            try {
                Result result = sampleAndWrite(cx, cy, cz, size, stride, effective, outFile, plane);
                // CommandSourceStack is not safe to touch from worker threads.
                server.execute(() -> src.sendSuccess(() -> Component.literal(String.format(
                        "[beyond_noise_dump] Done → %s  density=[%.3f, %.3f]  solid=%d/%d (%.1f%%)",
                        outFile.getName(), result.minDensity, result.maxDensity,
                        result.solidCount, size * size,
                        100.0 * result.solidCount / (double) (size * size)))
                        .withStyle(ChatFormatting.GREEN), false));
            } catch (Throwable t) {
                TheBeyond.LOGGER.error("[beyond_noise_dump] dump failed", t);
                server.execute(() -> src.sendFailure(Component.literal(
                        "[beyond_noise_dump] Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage())));
            }
        });

        return 1;
    }

    /**
     * Samples density on a {@code size × size} grid oriented by {@code plane}, emitting
     * grayscale-density PNG with red tint on solid cells (matches horizontal dump palette).
     *
     * <p>For {@link Plane#HORIZONTAL_XZ}: X runs across image columns, Z across rows (flipped
     * so +Z points down — JourneyMap convention), Y is constant at {@code cy}. For
     * {@link Plane#VERTICAL_XY}: X across columns, Y across rows (flipped so +Y points up —
     * world-space convention), Z is constant at {@code cz}. Sample Y may fall outside the
     * dim build height — {@code edgeGradient} inside {@code getTerrainDensity} clamps
     * density near the floor/ceiling so rows far above/below the world appear black.
     */
    private static Result sampleAndWrite(int cx, int cy, int cz, int size, int stride,
                                         BeyondTerrainParams params, File outFile,
                                         Plane plane) throws IOException {
        double[] densities = new double[size * size];
        boolean[] solid = new boolean[size * size];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int solidCount = 0;

        int half = size / 2;
        for (int iOuter = 0; iOuter < size; iOuter++) {
            for (int iInner = 0; iInner < size; iInner++) {
                int sampleX, sampleY, sampleZ;
                if (plane == Plane.VERTICAL_XY) {
                    sampleX = cx + (iInner - half) * stride;
                    sampleY = cy + (iOuter - half) * stride;
                    sampleZ = cz;
                } else { // HORIZONTAL_XZ
                    sampleX = cx + (iInner - half) * stride;
                    sampleY = cy;
                    sampleZ = cz + (iOuter - half) * stride;
                }
                double density = BeyondEndChunkGenerator.getTerrainDensity(sampleX, sampleY, sampleZ, params);
                // Threshold must be sampled at WRAPPED coords to match the chunk generator:
                // getTerrainDensity wraps internally, and isSolidTerrain / generateEndTerrain
                // both wrap before calling getThreshold. Diagnostic params apply so the dump
                // reflects the requested mode.
                long packed = BeyondEndChunkGenerator.computeWrappedCoords(sampleX, sampleZ, params);
                int wrappedSampleX = BeyondEndChunkGenerator.unpackWrappedX(packed);
                int wrappedSampleZ = BeyondEndChunkGenerator.unpackWrappedZ(packed);
                double threshold = BeyondEndChunkGenerator.getThreshold(wrappedSampleX, wrappedSampleZ, DISPLAY_DISTANCE_FROM_ORIGIN);
                int idx = iOuter * size + iInner;
                densities[idx] = density;
                if (density > threshold) {
                    solid[idx] = true;
                    solidCount++;
                }
                if (density < min) min = density;
                if (density > max) max = density;
            }
        }

        // Grayscale for density, red tint for solid cells — matches TerrainDensityGridDumpTest
        // so outputs can be overlaid visually.
        double range = Math.max(1e-9, max - min);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int iOuter = 0; iOuter < size; iOuter++) {
            for (int iInner = 0; iInner < size; iInner++) {
                int idx = iOuter * size + iInner;
                double norm = (densities[idx] - min) / range;
                int v = Math.max(0, Math.min(255, (int) Math.round(norm * 255.0)));
                int rgb;
                if (solid[idx]) {
                    rgb = (Math.min(255, v + 80) << 16) | ((v / 2) << 8) | (v / 2);
                } else {
                    rgb = (v << 16) | (v << 8) | v;
                }
                // Row flip matches the plane's natural orientation: XZ -> +Z points down
                // (JourneyMap), XY -> +Y points up (side view).
                img.setRGB(iInner, size - 1 - iOuter, rgb);
            }
        }
        ImageIO.write(img, "png", outFile);

        return new Result(min, max, solidCount);
    }

    private record Result(double minDensity, double maxDensity, int solidCount) {}
}
