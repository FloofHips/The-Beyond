package com.thebeyond.common.block.blockentities;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Sends the maps inside a projector to the players watching it, read only: only a player holding a map draws on it. */
public final class ProjectorMapSync {
    private static final int INTERVAL = 20;
    private static final Map<ServerPlayer, Object2IntMap<MapId>> SENT = new WeakHashMap<>();

    private ProjectorMapSync() {
    }

    static void tick(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), INTERVAL) != 0) {
            return;
        }
        List<ServerPlayer> watchers = null;
        for (ItemStack stack : items) {
            MapId id = stack.get(DataComponents.MAP_ID);
            if (id == null) {
                continue;
            }
            MapItemSavedData data = level.getMapData(id);
            if (data == null) {
                continue;
            }
            if (watchers == null) {
                watchers = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
                if (watchers.isEmpty()) {
                    return;
                }
            }
            int version = version(data);
            for (ServerPlayer player : watchers) {
                Object2IntMap<MapId> sent = SENT.computeIfAbsent(player, k -> new Object2IntOpenHashMap<>());
                if (sent.containsKey(id) && sent.getInt(id) == version) {
                    continue;
                }
                List<MapDecoration> decorations = new ArrayList<>();
                data.getDecorations().forEach(decorations::add);
                player.connection.send(new ClientboundMapItemDataPacket(id, data.scale, data.locked, decorations,
                        new MapItemSavedData.MapPatch(0, 0, 128, 128, data.colors.clone())));
                sent.put(id, version);
            }
        }
    }

    private static int version(MapItemSavedData data) {
        int h = Arrays.hashCode(data.colors);
        for (MapDecoration decoration : data.getDecorations()) {
            h = 31 * h + decoration.hashCode();
        }
        return 31 * h + data.scale + (data.locked ? 1 : 0);
    }
}
