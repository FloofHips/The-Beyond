package com.thebeyond.common.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureShape {
    private StructureShape() {}

    private static final Map<ResourceLocation, int[]> START = new ConcurrentHashMap<>();

    public static void reset() {
        START.clear();
    }

    /** {half of the widest start element, height of the tallest}, or {0, 0} when no template can be read. */
    public static int[] startExtent(Holder<StructureTemplatePool> start, StructureTemplateManager tm) {
        ResourceLocation id = start.unwrapKey().map(ResourceKey::location).orElse(null);
        return id == null ? extentOf(start.value(), tm) : START.computeIfAbsent(id, k -> extentOf(start.value(), tm));
    }

    private static int[] extentOf(StructureTemplatePool pool, StructureTemplateManager tm) {
        int wide = 0, tall = 0;
        for (StructurePoolElement e : distinct(pool)) {
            try {
                BoundingBox bb = e.getBoundingBox(tm, BlockPos.ZERO, Rotation.NONE);
                wide = Math.max(wide, Math.max(bb.getXSpan(), bb.getZSpan()));
                tall = Math.max(tall, bb.getYSpan());
            } catch (Throwable ignored) { }
        }
        return new int[]{wide / 2, tall};
    }

    private static Set<StructurePoolElement> distinct(StructureTemplatePool pool) {
        Set<StructurePoolElement> out = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        try { out.addAll(pool.getShuffledTemplates(RandomSource.create(0L))); } catch (Throwable ignored) { }
        return out;
    }
}
