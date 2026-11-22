package com.thebeyond.common.worldgen;

import com.thebeyond.BeyondConfig;
import com.thebeyond.TheBeyond;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;

/** End biome cell size, fixed per world: the dimension JSON is re-read on every load, so the choice lives in a world file. */
public final class BiomeCellScale {
    public static final double BASE_CELL_SIZE = 200.0;
    private static final String FILE = "the_beyond_biome_size.dat";
    private static final String KEY = "multiplier";
    private static final String HEIGHT_KEY = "height_multiplier";

    private static volatile double cellWidth = BASE_CELL_SIZE;
    private static volatile double cellHeight = BASE_CELL_SIZE;

    private BiomeCellScale() {}

    public static double cellWidth() {
        return cellWidth;
    }

    public static double cellHeight() {
        return cellHeight;
    }

    public static void resolve(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE);
        double width;
        double height;
        String origin;
        try {
            if (Files.exists(file)) {
                CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                width = tag.getDouble(KEY);
                height = tag.contains(HEIGHT_KEY) ? tag.getDouble(HEIGHT_KEY) : width;
                origin = "kept from world creation";
            } else {
                // older worlds were made at 1.0, and the Beyond alone keeps the cells its biomes were tuned for
                boolean newWorld = !server.getWorldData().overworldData().isInitialized();
                boolean isleweaver = ModList.get().isLoaded("isleweaver");
                double widthSetting = BeyondConfig.BIOME_WIDTH_MULTIPLIER.get();
                double heightSetting = BeyondConfig.BIOME_HEIGHT_MULTIPLIER.get();
                width = newWorld ? chosen(widthSetting, isleweaver) : 1.0;
                height = newWorld ? chosen(heightSetting, isleweaver) : 1.0;
                String source = widthSetting > 0 && heightSetting > 0 ? "from config"
                        : widthSetting > 0 || heightSetting > 0 ? "partly from config" : "automatic";
                origin = !newWorld ? "world older than the setting"
                        : "new world " + (isleweaver ? "with" : "without") + " Isleweaver, " + source;
                CompoundTag tag = new CompoundTag();
                tag.putDouble(KEY, width);
                tag.putDouble(HEIGHT_KEY, height);
                Files.createDirectories(file.getParent());
                NbtIo.writeCompressed(tag, file);
            }
        } catch (Exception e) {
            TheBeyond.LOGGER.error("[TheBeyond] Could not read or write {}, End biome size falls back to 1.0", file, e);
            width = 1.0;
            height = 1.0;
            origin = "fallback";
        }
        if (!(width > 0) || !(height > 0)) {
            TheBeyond.LOGGER.warn("[TheBeyond] Invalid End biome size {} by {} in {}, using 1.0", width, height, file);
            width = 1.0;
            height = 1.0;
        }
        cellWidth = BASE_CELL_SIZE * width;
        cellHeight = BASE_CELL_SIZE * height;
        TheBeyond.LOGGER.info("[TheBeyond] End biome size x{} wide, x{} tall ({} by {} block cells, {})",
                width, height, cellWidth, cellHeight, origin);
    }

    private static double chosen(double setting, boolean isleweaver) {
        return setting > 0 ? setting : isleweaver ? 2.0 : 1.0;
    }

    public static void reset() {
        cellWidth = BASE_CELL_SIZE;
        cellHeight = BASE_CELL_SIZE;
    }
}
