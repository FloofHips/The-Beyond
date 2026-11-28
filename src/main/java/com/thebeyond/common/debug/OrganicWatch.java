package com.thebeyond.common.debug;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Records every network block the painter writes or skips, and who writes over it later (org_watch.txt). */
public final class OrganicWatch {
    public static final boolean ACTIVE = Boolean.getBoolean("beyond.sub.org.watch");
    private static final Long2ObjectOpenHashMap<Block> PAINTED = new Long2ObjectOpenHashMap<>();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Set<String> PLUMBING = Set.of("OrganicWatch", "ProtoChunk", "LevelChunk", "ChunkAccess",
            "WorldGenRegion", "Level", "ServerLevel", "LevelWriter", "LevelAccessor", "WorldGenLevel");
    private static BufferedWriter out;

    private OrganicWatch() {}

    public static synchronized void painted(BlockPos pos, Block b) {
        PAINTED.put(pos.asLong(), b);
        write("P " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + name(b));
    }

    public static synchronized void skipped(BlockPos pos, BlockState found) {
        write("S " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + name(found.getBlock()));
    }

    public static void note(ChunkAccess chunk, BlockPos pos, BlockState state) {
        Block b;
        synchronized (OrganicWatch.class) {
            b = PAINTED.get(pos.asLong());
        }
        if (b == null || state.is(b) || !chunk.getBlockState(pos).is(b)) return;
        String calls = WALKER.walk(s -> s
                .filter(f -> !PLUMBING.contains(f.getDeclaringClass().getSimpleName()) && !f.getMethodName().contains("the_beyond$watch"))
                .limit(5)
                .map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getMethodName() + ":" + f.getLineNumber())
                .reduce((x, y) -> x + "<" + y).orElse("?"));
        String writer = WALKER.walk(s -> s.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(c -> Feature.class.isAssignableFrom(c) || StructurePiece.class.isAssignableFrom(c))
                .map(Class::getSimpleName).findFirst().orElse("-"));
        synchronized (OrganicWatch.class) {
            write("W " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + name(b) + " " + name(state.getBlock()) + " "
                    + chunk.getPersistedStatus().getName() + " " + writer + " " + calls);
            flush();
        }
    }

    public static synchronized void flush() {
        try {
            if (out != null) out.flush();
        } catch (IOException ignored) {
        }
    }

    private static String name(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).getPath();
    }

    private static void write(String line) {
        try {
            if (out == null) {
                Path p = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("org_watch.txt");
                out = Files.newBufferedWriter(p, StandardCharsets.UTF_8);
            }
            out.write(line);
            out.newLine();
        } catch (IOException e) {
            com.thebeyond.TheBeyond.LOGGER.warn("ORG watch write failed: {}", e.toString());
        }
    }
}
