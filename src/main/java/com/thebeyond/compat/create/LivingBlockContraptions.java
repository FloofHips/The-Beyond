package com.thebeyond.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.thebeyond.common.entity.util.livingblock.LivingBlockFrames;
import com.thebeyond.common.entity.util.livingblock.OrientedBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Create contraption blocks as terrain for living blocks. Loaded only after {@code isLoaded("create")}. */
final class LivingBlockContraptions implements LivingBlockFrames.ExtraTerrain {
    // every terrain probe asks for contraptions, so a level without any must answer without an entity search
    private static final Map<Level, Set<AbstractContraptionEntity>> LOADED = Collections.synchronizedMap(new WeakHashMap<>());

    static void joined(Entity entity, Level level) {
        if (entity instanceof AbstractContraptionEntity contraption) {
            LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new IdentityHashMap<>())).add(contraption);
        }
    }

    static void left(Entity entity, Level level) {
        if (entity instanceof AbstractContraptionEntity contraption) {
            Set<AbstractContraptionEntity> here = LOADED.get(level);
            if (here != null) {
                here.remove(contraption);
            }
        }
    }

    @Override
    public void collect(Level level, AABB box, List<OrientedBox> out) {
        Set<AbstractContraptionEntity> here = LOADED.get(level);
        if (here == null || here.isEmpty()) {
            return;
        }
        AABB reach = box.inflate(2.0);
        for (AbstractContraptionEntity entity : here) {
            if (!entity.isAlive() || !entity.getBoundingBox().intersects(reach)) {
                continue;
            }
            Contraption contraption = entity.getContraption();
            if (contraption == null) {
                continue;
            }
            Vec3 origin = entity.toGlobalVector(Vec3.ZERO, 1.0F);
            Vec3 axisX = entity.toGlobalVector(new Vec3(1.0, 0.0, 0.0), 1.0F).subtract(origin);
            Vec3 axisY = entity.toGlobalVector(new Vec3(0.0, 1.0, 0.0), 1.0F).subtract(origin);
            Vec3 axisZ = entity.toGlobalVector(new Vec3(0.0, 0.0, 1.0), 1.0F).subtract(origin);
            AABB local = localBounds(entity, box);
            BlockPos min = BlockPos.containing(local.minX, local.minY, local.minZ);
            BlockPos max = BlockPos.containing(local.maxX, local.maxY, local.maxZ);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                StructureTemplate.StructureBlockInfo info = contraption.getBlocks().get(pos);
                if (info == null || contraption.isHiddenInPortal(pos)) {
                    continue;
                }
                VoxelShape shape = info.state().getCollisionShape(level, pos);
                if (shape.isEmpty()) {
                    continue;
                }
                for (AABB part : shape.toAabbs()) {
                    AABB placed = part.move(pos);
                    Vec3 center = entity.toGlobalVector(placed.getCenter(), 1.0F);
                    out.add(OrientedBox.spanning(center, placed.getXsize() * 0.5, placed.getYsize() * 0.5,
                            placed.getZsize() * 0.5, axisX, axisY, axisZ));
                }
            }
        }
    }

    private static AABB localBounds(AbstractContraptionEntity entity, AABB box) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ);
            Vec3 local = entity.toLocalVector(corner, 1.0F);
            minX = Math.min(minX, local.x);
            minY = Math.min(minY, local.y);
            minZ = Math.min(minZ, local.z);
            maxX = Math.max(maxX, local.x);
            maxY = Math.max(maxY, local.y);
            maxZ = Math.max(maxZ, local.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
