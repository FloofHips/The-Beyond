package com.thebeyond.common.entity.util.livingblock;

import com.google.common.collect.Lists;
import com.thebeyond.TheBeyond;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** A per-tick grid answering living block entity queries in vanilla's order: section x, unsigned packed z and y, then rank. */
public final class LivingBlockNeighbourhood {
    private static final Side CLIENT = new Side();
    private static final Side SERVER = new Side();
    private static final Comparator<Member> VANILLA_ORDER = (a, b) -> {
        if (a.sec.x != b.sec.x) return Integer.compare(a.sec.x, b.sec.x);
        if (a.sec.z != b.sec.z) return Integer.compare(a.sec.z & 0x3FFFFF, b.sec.z & 0x3FFFFF);
        if (a.sec.y != b.sec.y) return Integer.compare(a.sec.y & 0xFFFFF, b.sec.y & 0xFFFFF);
        return Integer.compare(a.rank, b.rank);
    };

    /** Set by the Sable compat: sub-levels add entities from their plots, so queries near one go to vanilla. */
    public static BiPredicate<Level, AABB> subLevelsNear = (level, box) -> false;

    public static boolean enabled = true;
    public static boolean verify;
    public static boolean stats;
    private static long served;
    private static long indexed;
    private static long mismatches;
    private static long lastReport;

    private LivingBlockNeighbourhood() {}

    public interface Sections {
        EntitySectionStorage<Entity> the_beyond$entitySections();
    }

    private static final class Sec {
        final long key;
        final int x, y, z;
        boolean accessible;
        boolean resolved;
        int nextRank;

        Sec(long key) {
            this.key = key;
            this.x = SectionPos.x(key);
            this.y = SectionPos.y(key);
            this.z = SectionPos.z(key);
        }
    }

    private static final class Member {
        final Entity entity;
        final Sec sec;
        final int rank;
        int cx0, cy0, cz0, cx1, cy1, cz1;
        int mark;

        Member(Entity entity, Sec sec, int rank) {
            this.entity = entity;
            this.sec = sec;
            this.rank = rank;
        }
    }

    private static final class Side {
        @Nullable
        Level level;
        @Nullable
        EntitySectionStorage<Entity> storage;
        long tick;
        int mark;
        volatile boolean stale;
        final Long2ObjectOpenHashMap<Sec> sections = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<ArrayList<Member>> cells = new Long2ObjectOpenHashMap<>();
        final Reference2ObjectOpenHashMap<Entity, Member> members = new Reference2ObjectOpenHashMap<>();
        final ArrayList<Member> found = new ArrayList<>();

        void reset() {
            this.level = null;
            this.storage = null;
            this.sections.clear();
            this.cells.clear();
            this.members.clear();
        }

        boolean ready(Level level, AABB area) {
            if (!enabled || !(level instanceof Sections holder)) {
                return false;
            }
            if (this.stale || level != this.level || level.getGameTime() != this.tick) {
                this.stale = false;
                this.reset();
                this.level = level;
                this.storage = holder.the_beyond$entitySections();
                this.tick = level.getGameTime();
            }
            return this.storage != null && !subLevelsNear.test(level, area);
        }

        void section(long key) {
            if (this.sections.containsKey(key)) {
                return;
            }
            Sec sec = new Sec(key);
            this.sections.put(key, sec);
            EntitySection<Entity> section = this.storage.getSection(key);
            if (section != null) {
                sec.accessible = section.getStatus().isAccessible();
                sec.resolved = true;
                section.getEntities().forEach(entity -> this.join(entity, sec));
                if (stats || verify) {
                    indexed++;
                }
            }
        }

        void join(Entity entity, Sec sec) {
            Member member = new Member(entity, sec, sec.nextRank++);
            this.members.put(entity, member);
            this.register(member, entity.getBoundingBox());
        }

        void register(Member member, AABB box) {
            member.cx0 = Mth.floor(box.minX) >> 1;
            member.cy0 = Mth.floor(box.minY) >> 1;
            member.cz0 = Mth.floor(box.minZ) >> 1;
            member.cx1 = Mth.floor(box.maxX) >> 1;
            member.cy1 = Mth.floor(box.maxY) >> 1;
            member.cz1 = Mth.floor(box.maxZ) >> 1;
            for (int x = member.cx0; x <= member.cx1; x++) {
                for (int y = member.cy0; y <= member.cy1; y++) {
                    for (int z = member.cz0; z <= member.cz1; z++) {
                        this.cells.computeIfAbsent(BlockPos.asLong(x, y, z), k -> new ArrayList<>()).add(member);
                    }
                }
            }
        }

        void unregister(Member member) {
            for (int x = member.cx0; x <= member.cx1; x++) {
                for (int y = member.cy0; y <= member.cy1; y++) {
                    for (int z = member.cz0; z <= member.cz1; z++) {
                        ArrayList<Member> cell = this.cells.get(BlockPos.asLong(x, y, z));
                        if (cell != null) {
                            cell.remove(member);
                        }
                    }
                }
            }
        }

        List<Member> collect(AABB area) {
            int x0 = SectionPos.posToSectionCoord(area.minX - 2.0), y0 = SectionPos.posToSectionCoord(area.minY - 4.0);
            int z0 = SectionPos.posToSectionCoord(area.minZ - 2.0), x1 = SectionPos.posToSectionCoord(area.maxX + 2.0);
            int y1 = SectionPos.posToSectionCoord(area.maxY), z1 = SectionPos.posToSectionCoord(area.maxZ + 2.0);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        this.section(SectionPos.asLong(x, y, z));
                    }
                }
            }
            int mark = ++this.mark;
            ArrayList<Member> found = this.found;
            found.clear();
            int cx0 = Mth.floor(area.minX) >> 1, cy0 = Mth.floor(area.minY) >> 1, cz0 = Mth.floor(area.minZ) >> 1;
            int cx1 = Mth.floor(area.maxX) >> 1, cy1 = Mth.floor(area.maxY) >> 1, cz1 = Mth.floor(area.maxZ) >> 1;
            for (int x = cx0; x <= cx1; x++) {
                for (int y = cy0; y <= cy1; y++) {
                    for (int z = cz0; z <= cz1; z++) {
                        ArrayList<Member> cell = this.cells.get(BlockPos.asLong(x, y, z));
                        if (cell == null) {
                            continue;
                        }
                        for (int i = 0; i < cell.size(); i++) {
                            Member member = cell.get(i);
                            if (member.mark == mark) {
                                continue;
                            }
                            member.mark = mark;
                            Sec sec = member.sec;
                            if (sec.accessible && sec.x >= x0 && sec.x <= x1 && sec.y >= y0 && sec.y <= y1
                                    && sec.z >= z0 && sec.z <= z1 && member.entity.getBoundingBox().intersects(area)) {
                                found.add(member);
                            }
                        }
                    }
                }
            }
            found.sort(VANILLA_ORDER);
            return found;
        }
    }

    private static Side side(Level level) {
        return level.isClientSide() ? CLIENT : SERVER;
    }

    public static void onBoundsChange(Entity entity, AABB next) {
        Level level = entity.level();
        if (level == null) {
            return;
        }
        Side side = side(level);
        if (side.level != level) {
            return;
        }
        Member member = side.members.get(entity);
        if (member != null) {
            side.unregister(member);
            side.register(member, next);
        }
    }

    /** An entity entering a section is appended to its list, and leaving keeps the others' order. */
    public static void onSectionJoin(Object member) {
        if (!(member instanceof Entity entity) || entity.level() == null) {
            return;
        }
        Side side = side(entity.level());
        if (side.level != entity.level()) {
            return;
        }
        Member old = side.members.remove(entity);
        if (old != null) {
            side.unregister(old);
        }
        Sec sec = side.sections.get(SectionPos.asLong(entity.blockPosition()));
        if (sec != null) {
            if (!sec.resolved) {
                EntitySection<Entity> section = side.storage.getSection(sec.key);
                sec.accessible = section != null && section.getStatus().isAccessible();
                sec.resolved = true;
            }
            side.join(entity, sec);
        }
    }

    public static void onSectionLeave(Object member) {
        if (!(member instanceof Entity entity) || entity.level() == null) {
            return;
        }
        Side side = side(entity.level());
        if (side.level != entity.level()) {
            return;
        }
        Member old = side.members.remove(entity);
        if (old != null) {
            side.unregister(old);
        }
    }

    // single player calls this from both threads, so a side is only flagged here and rebuilt by its own thread
    public static void onVisibilityChange() {
        CLIENT.stale = true;
        SERVER.stale = true;
    }

    public static List<Entity> getEntities(Level level, @Nullable Entity except, AABB area, Predicate<? super Entity> predicate) {
        Side side = side(level);
        if (!side.ready(level, area)) {
            return level.getEntities(except, area, predicate);
        }
        level.getProfiler().incrementCounter("getEntities");
        List<Entity> list = Lists.newArrayList();
        for (Member member : side.collect(area)) {
            Entity entity = member.entity;
            if (entity != except && predicate.test(entity)) {
                list.add(entity);
            }
        }
        for (PartEntity<?> part : level.getPartEntities()) {
            if (part != except && part.getBoundingBox().intersects(area) && predicate.test(part)) {
                list.add(part);
            }
        }
        report(list, verify ? level.getEntities(except, area, predicate) : null);
        return list;
    }

    public static <T extends Entity> List<T> getEntitiesOfClass(Level level, Class<T> type, AABB area, Predicate<? super T> predicate) {
        Side side = side(level);
        if (!side.ready(level, area)) {
            return level.getEntitiesOfClass(type, area, predicate);
        }
        level.getProfiler().incrementCounter("getEntities");
        EntityTypeTest<Entity, T> test = EntityTypeTest.forClass(type);
        List<T> list = Lists.newArrayList();
        for (Member member : side.collect(area)) {
            T cast = test.tryCast(member.entity);
            if (cast != null && predicate.test(cast)) {
                list.add(cast);
            }
        }
        for (PartEntity<?> part : level.getPartEntities()) {
            T cast = test.tryCast(part);
            if (cast != null && cast.getBoundingBox().intersects(area) && predicate.test(cast)) {
                list.add(cast);
            }
        }
        report(list, verify ? level.getEntitiesOfClass(type, area, predicate) : null);
        return list;
    }

    private static <T> void report(List<T> ours, @Nullable List<T> vanilla) {
        if (vanilla != null) {
            boolean same = ours.size() == vanilla.size();
            for (int i = 0; same && i < ours.size(); i++) {
                same = ours.get(i) == vanilla.get(i);
            }
            if (!same && ++mismatches <= 5) {
                TheBeyond.LOGGER.warn("[Beyond] neighbourhood mismatch: ours {} vanilla {}", ours.size(), vanilla.size());
            }
        }
        if ((verify || stats) && ++served - lastReport >= 20000) {
            lastReport = served;
            TheBeyond.LOGGER.info("[Beyond] neighbourhood: {} queries, {} mismatches, {} sections indexed", served, mismatches, indexed);
        }
    }
}
