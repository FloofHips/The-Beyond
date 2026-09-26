package com.thebeyond.common.debug;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.nio.file.Files;
import java.nio.file.Path;

/** Logs every write to the cells in config/the_beyond_watch.txt with the chunk status and callers, off when it is absent. */
public final class BlockWatch {
    private BlockWatch() {}

    private static final LongOpenHashSet WATCH = load();
    public static final boolean ACTIVE = !WATCH.isEmpty();

    private static LongOpenHashSet load() {
        LongOpenHashSet out = new LongOpenHashSet();
        try {
            Path p = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("config/the_beyond_watch.txt");
            if (!Files.exists(p)) return out;
            for (String line : Files.readAllLines(p)) {
                String[] v = line.trim().split("[ ,]+");
                if (v.length < 3 || v[0].startsWith("#")) continue;
                out.add(BlockPos.asLong(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])));
            }
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond watch] watching {} block positions", out.size());
        } catch (Throwable t) {
            com.thebeyond.TheBeyond.LOGGER.warn("[Beyond watch] watch list unreadable: {}", t.toString());
        }
        return out;
    }

    public static void note(ChunkAccess chunk, BlockPos pos, BlockState state) {
        if (!WATCH.contains(pos.asLong())) return;
        BlockState old = chunk.getBlockState(pos);
        String calls = StackWalker.getInstance().walk(s -> s
                .filter(f -> !f.getClassName().endsWith("BlockWatch") && !f.getMethodName().contains("the_beyond$watch"))
                .limit(9)
                .map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName() + ":" + f.getLineNumber())
                .reduce((a, b) -> a + " < " + b).orElse("?"));
        com.thebeyond.TheBeyond.LOGGER.info("[Beyond watch] {} {} {}: {} -> {} at {} by {}", pos.getX(), pos.getY(), pos.getZ(),
                old, state, chunk.getPersistedStatus(), calls);
    }
}
