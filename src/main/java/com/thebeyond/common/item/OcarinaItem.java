package com.thebeyond.common.item;

import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.client.particle.CircleColorTransitionOptions;
import com.thebeyond.client.particle.CrosshairColorTransitionOptions;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.entity.BeadEntity;
import com.thebeyond.common.entity.util.livingblock.LivingBlockOrientation;
import com.thebeyond.common.entity.util.livingblock.movement.Target;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.common.registry.BeyondSoundEvents;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.thebeyond.common.block.MemorFaucetBlock.AGE;

public class OcarinaItem extends Item {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final double SLOT_MARGIN = 0.1;
    private static final double SLOT_GAP = 0.002;
    private static final double ARRIVAL_RADIUS = 0.25;
    private static final double LAYER_ARRIVAL_FRACTION = 0.6;
    private static final int MAX_GROUND_DROP = 8;
    private static final int MAX_LINE = 5;
    private static final double WIDTH_EPSILON = 1.0E-6;

    public OcarinaItem(Properties properties) {
        super(properties);
    }

    private record Slot(int x, int z, int layer) {}

    private record Placed(BeadEntity bead, Slot slot, double y) {}

    private record Line(int index, double width, int columns, int count) {}

    private static List<Line> lines(final List<BeadEntity> beads) {
        Map<Double, Integer> histogram = new HashMap<>();
        for (BeadEntity bead : beads) {
            histogram.merge(footprint(bead).getXsize(), 1, Integer::sum);
        }
        List<double[]> groups = new ArrayList<>();
        List<Double> widths = new ArrayList<>(histogram.keySet());
        widths.sort(Comparator.reverseOrder());
        for (double width : widths) {
            int total = histogram.get(width);
            int count = Math.max(1, Math.min(MAX_LINE, (int) Math.round(Math.sqrt(total))));
            int weight = count * (count + 1) / 2;
            int left = total;
            for (int line = 0; line < count; line++) {
                int share = line == count - 1 ? left
                        : Math.max(1, Math.min(left - (count - 1 - line), total * (count - line) / weight));
                groups.add(new double[] {width, share});
                left -= share;
            }
        }
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            int count = (int) groups.get(i)[1];
            out.add(new Line(i % 2 == 0 ? i / 2 : -(i / 2 + 1), groups.get(i)[0],
                    triangleBase(count), count));
        }
        return out;
    }

    private static int triangleBase(final int count) {
        int base = 1;
        while (base * (base + 1) / 2 < count) {
            base++;
        }
        return base;
    }

    private static List<Slot> slotOrder(final List<Line> lines) {
        List<Slot> order = new ArrayList<>();
        for (Line line : lines) {
            int left = line.count();
            for (int layer = 0, wide = line.columns(); left > 0 && wide > 0; layer++, wide--) {
                for (int i = 0; i < wide && left > 0; i++, left--) {
                    order.add(new Slot(line.index(), i % 2 == 0 ? i / 2 : -(i / 2 + 1), layer));
                }
            }
        }
        return order;
    }

    private static Map<Integer, Double> axisCentres(final Map<Integer, Double> widths) {
        Map<Integer, Double> centres = new HashMap<>();
        centres.put(0, 0.0);
        int high = widths.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int low = widths.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
        for (int i = 1; i <= high; i++) {
            centres.put(i, centres.getOrDefault(i - 1, 0.0)
                    + halfWidth(widths, i - 1) + SLOT_GAP + halfWidth(widths, i));
        }
        for (int i = -1; i >= low; i--) {
            centres.put(i, centres.getOrDefault(i + 1, 0.0)
                    - halfWidth(widths, i + 1) - SLOT_GAP - halfWidth(widths, i));
        }
        return centres;
    }

    private static double halfWidth(final Map<Integer, Double> widths, final int index) {
        return widths.getOrDefault(index, 0.0) * 0.5;
    }

    private static AABB footprint(final BeadEntity bead) {
        AABB base = bead.getBaseShapeBounds();
        double[] local = {base.getXsize(), base.getYsize(), base.getZsize()};
        double[] world = new double[3];
        LivingBlockOrientation orientation = bead.getOrientation();
        for (Direction.Axis axis : Direction.Axis.values()) {
            world[orientation.worldAxisOf(axis).ordinal()] = local[axis.ordinal()];
        }
        return new AABB(0.0, 0.0, 0.0, world[0], world[1], world[2]);
    }

    private static int reservedFace(final BeadEntity bead) {
        AABB box = footprint(bead);
        return Math.abs(box.getXsize() - box.getYsize()) < WIDTH_EPSILON
                && Math.abs(box.getYsize() - box.getZsize()) < WIDTH_EPSILON
                ? Target.ANY_FACE : bead.getOrientation().index();
    }

    private static void assignSlots(final Level level, final List<BeadEntity> beads, final Vec3 center) {
        if (beads.isEmpty()) {
            return;
        }
        List<Line> lines = lines(beads);
        Map<Integer, Double> lineWidth = new HashMap<>();
        Map<Integer, Double> lineDepth = new HashMap<>();
        for (Line line : lines) {
            lineWidth.put(line.index(), line.width());
            for (BeadEntity bead : beads) {
                AABB box = footprint(bead);
                if (Math.abs(box.getXsize() - line.width()) < WIDTH_EPSILON) {
                    lineDepth.merge(line.index(), box.getZsize(), Math::max);
                }
            }
        }
        Map<Integer, Double> centreX = axisCentres(lineWidth);
        double layerHeight = beads.stream().mapToDouble(bead -> footprint(bead).getYsize())
                .max().orElse(SLOT_MARGIN);
        double arrival = Math.min(ARRIVAL_RADIUS, layerHeight * LAYER_ARRIVAL_FRACTION);

        Set<BeadEntity> commanded = Set.copyOf(beads);
        List<AABB> reserved = new ArrayList<>(beads.size());
        Map<Long, Integer> stacked = new HashMap<>();
        Map<Long, Double> tops = new HashMap<>();
        List<BeadEntity> free = new ArrayList<>(beads);
        List<Placed> taken = new ArrayList<>(beads.size());

        for (Slot slot : slotOrder(lines)) {
            if (free.isEmpty()) {
                break;
            }
            long column = column(slot.x(), slot.z());
            if (stacked.getOrDefault(column, 0) != slot.layer()) {
                continue;
            }
            double width = lineWidth.getOrDefault(slot.x(), SLOT_MARGIN);
            double depth = lineDepth.getOrDefault(slot.x(), width);
            Vec3 spot = new Vec3(center.x + centreX.getOrDefault(slot.x(), 0.0),
                    tops.getOrDefault(column, center.y),
                    center.z + slot.z() * (depth + SLOT_GAP));

            BeadEntity bead = null;
            AABB probe = null;
            double best = Double.MAX_VALUE;
            int byWidth = 0;
            int byBlock = 0;
            int byEntity = 0;
            for (BeadEntity candidate : free) {
                AABB box = footprint(candidate);
                if (Math.abs(box.getXsize() - width) >= WIDTH_EPSILON) {
                    byWidth++;
                    continue;
                }
                AABB cell = slotBox(box, spot);
                if (!level.noBlockCollision(candidate, cell)) {
                    byBlock++;
                    continue;
                }
                if (!level.getEntities(candidate, cell,
                        other -> other.canBeCollidedWith() && !commanded.contains(other)).isEmpty()) {
                    byEntity++;
                    continue;
                }
                if (overlapsReserved(reserved, cell)) {
                    continue;
                }
                double cost = candidate.position().distanceToSqr(spot);
                if (cost < best) {
                    best = cost;
                    bead = candidate;
                    probe = cell;
                }
            }
            if (bead == null) {
                LOGGER.debug("[ocarina] miss line={} col={} layer={} width={} free={} bywidth={} byblock={} byentity={}",
                        slot.x(), slot.z(), slot.layer(), String.format("%.3f", width),
                        free.size(), byWidth, byBlock, byEntity);
                continue;
            }

            free.remove(bead);
            reserved.add(probe);
            stacked.put(column, slot.layer() + 1);
            tops.put(column, spot.y + footprint(bead).getYsize());
            taken.add(new Placed(bead, slot, spot.y));
        }

        Map<Integer, Map<Integer, Double>> columnDepth = new HashMap<>();
        for (Placed entry : taken) {
            columnDepth.computeIfAbsent(entry.slot().x(), line -> new HashMap<>())
                    .merge(entry.slot().z(), footprint(entry.bead()).getZsize(), Math::max);
        }
        Map<Integer, Map<Integer, Double>> centreZ = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, Double>> line : columnDepth.entrySet()) {
            centreZ.put(line.getKey(), axisCentres(line.getValue()));
        }
        for (Placed entry : taken) {
            Map<Integer, Double> alongZ = centreZ.getOrDefault(entry.slot().x(), Map.of());
            Vec3 spot = new Vec3(center.x + centreX.getOrDefault(entry.slot().x(), 0.0), entry.y(),
                    center.z + alongZ.getOrDefault(entry.slot().z(), 0.0));
            BeadEntity bead = entry.bead();
            bead.reissueMovementTarget(Target.facing(spot, arrival, reservedFace(bead)));
            AABB box = footprint(bead);
            LOGGER.debug("[ocarina] slot id={} line={} col={} layer={} spot={} body={} face={} gapX={} gapZ={}",
                    bead.getId(), entry.slot().x(), entry.slot().z(), entry.slot().layer(),
                    String.format("%.2f,%.2f,%.2f", spot.x, spot.y, spot.z),
                    String.format("%.3f x %.3f", box.getXsize(), box.getZsize()),
                    reservedFace(bead),
                    String.format("%.3f", plannedGap(lineWidth, centreX, entry.slot().x())),
                    String.format("%.3f", plannedGap(columnDepth.get(entry.slot().x()), alongZ,
                            entry.slot().z())));
        }

        for (BeadEntity leftover : free) {
            leftover.clearMovementTarget();
        }
        LOGGER.debug("[ocarina] plan placed={} leftover={} lines={}",
                taken.size(), free.size(), lines.size());
    }

    private static double plannedGap(final Map<Integer, Double> widths,
                                     final Map<Integer, Double> centres, final int index) {
        int inner = index > 0 ? index - 1 : index + 1;
        Double here = index == 0 ? null : widths.get(index);
        Double neighbour = here == null ? null : widths.get(inner);
        if (neighbour == null) {
            return Double.NaN;
        }
        return Math.abs(centres.getOrDefault(index, 0.0) - centres.getOrDefault(inner, 0.0))
                - (here + neighbour) * 0.5;
    }

    private static long column(final int x, final int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    private static AABB slotBox(final AABB size, final Vec3 spot) {
        double halfX = size.getXsize() * 0.5;
        double halfZ = size.getZsize() * 0.5;
        return new AABB(spot.x - halfX, spot.y, spot.z - halfZ,
                spot.x + halfX, spot.y + size.getYsize(), spot.z + halfZ);
    }

    private static BlockPos groundedBase(final Level level, final BlockPos stand) {
        BlockPos base = stand;
        for (int drop = 0; drop < MAX_GROUND_DROP; drop++) {
            BlockPos below = base.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return base;
            }
            base = below;
        }
        return base;
    }

    private static boolean overlapsReserved(final List<AABB> reserved, final AABB probe) {
        for (AABB taken : reserved) {
            if (taken.intersects(probe)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);

        Vec3 eyePos = player.getEyePosition();
        Vec3 endPos = eyePos.add(player.getLookAngle().scale(64));

        if (!level.isClientSide) {
            ClipContext clipContext = new ClipContext(
                    eyePos,
                    endPos,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            );

            BlockHitResult hit = level.clip(clipContext);

            if (hit.getType() != HitResult.Type.MISS) {
                BlockPos pos = hit.getBlockPos();

                BlockPos a = player.getOnPos();
                AABB detectionBox = new AABB(a).inflate(8);

                BlockPos stand = groundedBase(level, pos.relative(hit.getDirection()));
                Vec3 center = new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                List<BeadEntity> entities = level.getEntitiesOfClass(BeadEntity.class, detectionBox);
                entities.sort(Comparator.comparingDouble(bead -> bead.distanceToSqr(center)));
                assignSlots(level, entities, center);
                return InteractionResultHolder.success(itemstack);
            }
        }
        return super.use(level, player, usedHand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        return super.finishUsingItem(stack, level, livingEntity);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return super.getUseDuration(stack, entity);
    }
}
