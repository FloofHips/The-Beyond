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

/** Dumps a PNG of the terrain density over a grid. Runs off-thread so it doesn't fight chunk-gen workers. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class BeyondNoiseDumpCommand {

    private BeyondNoiseDumpCommand() {}

    private static final int DEFAULT_SIZE = 200;
    private static final int DEFAULT_STRIDE = 2;
    private static final float DISPLAY_DISTANCE_FROM_ORIGIN = 1500f;

    private static final String DUMP_SUBDIR = "beyond_noise_dumps";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** Which way to slice: top-down at a fixed Y, or a side view at a fixed Z. */
    private enum Plane {
        HORIZONTAL_XZ("xz"),
        VERTICAL_XY("xy");

        final String suffix;
        Plane(String suffix) { this.suffix = suffix; }
    }

    /** Lets you turn off warp/wrap to see what each one contributes. */
    private enum Mode {
        NORMAL,
        NO_WARP,
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

        // Each .then() chain fakes optional args by giving every node its own .executes().
        LiteralArgumentBuilder<CommandSourceStack> noiseDump = Commands.literal("noise_dump");

        noiseDump.then(buildHereBranch(Plane.HORIZONTAL_XZ));
        noiseDump.then(buildAtBranch(Plane.HORIZONTAL_XZ));

        noiseDump.then(Commands.literal("vertical")
                .then(buildHereBranch(Plane.VERTICAL_XY))
                .then(buildAtBranch(Plane.VERTICAL_XY)));

        // Hangs off the shared /the_beyond root.
        dispatcher.register(Commands.literal("the_beyond")
                .requires(src -> src.hasPermission(2))
                .then(noiseDump));
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
            src.sendFailure(Component.literal("/the_beyond noise_dump here requires an entity source — use 'at <pos>' from the console."));
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

        // Snapshot the params here so a mid-dump datapack reload can't change them under the worker.
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
                String.format("[the_beyond noise_dump] Sampling %dx%d %s grid @ (%d, %d, %d) stride=%d mode=%s → %s",
                        size, size, plane.suffix, cx, cy, cz, stride, modeLabel, fileName))
                .withStyle(ChatFormatting.GRAY), false);

        CompletableFuture.runAsync(() -> {
            try {
                Result result = sampleAndWrite(cx, cy, cz, size, stride, effective, outFile, plane);
                // Bounce back to the main thread; the source isn't safe to touch off-thread.
                server.execute(() -> src.sendSuccess(() -> Component.literal(String.format(
                        "[the_beyond noise_dump] Done → %s  density=[%.3f, %.3f]  solid=%d/%d (%.1f%%)",
                        outFile.getName(), result.minDensity, result.maxDensity,
                        result.solidCount, size * size,
                        100.0 * result.solidCount / (double) (size * size)))
                        .withStyle(ChatFormatting.GREEN), false));
            } catch (Throwable t) {
                TheBeyond.LOGGER.error("[the_beyond noise_dump] dump failed", t);
                server.execute(() -> src.sendFailure(Component.literal(
                        "[the_beyond noise_dump] Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage())));
            }
        });

        return 1;
    }

    /** Samples the grid and writes a grayscale PNG, with solid cells tinted red. */
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
                // Wrap the coords before reading the threshold, exactly like the chunk generator does,
                // so the solid/not-solid call here matches the real terrain.
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
                // Flip the row so the image is oriented the way you'd expect to look at it.
                img.setRGB(iInner, size - 1 - iOuter, rgb);
            }
        }
        ImageIO.write(img, "png", outFile);

        return new Result(min, max, solidCount);
    }

    private record Result(double minDensity, double maxDensity, int solidCount) {}
}
