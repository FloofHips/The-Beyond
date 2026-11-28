package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.mixin.BeardifierAccessor;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.levelgen.Beardifier;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/** Tells when a chunk's Beardifier adds nothing anywhere, counting the terms other mods merge into it. */
final class BeardSkip {
    private static final Set<String> KNOWN_MIXINS = Set.of(
            "com.thebeyond.mixin.BeardifierAccessor",
            "dev.worldgen.lithostitched.mixin.common.BeardifierMixin",
            "com.finndog.moogs_structures.mixins.terrainadaptation.BeardifierMixin",
            "com.finndog.moogs_structures.mixins.terrainadaptation.BeardifierAccessor");
    private static final String MIXIN_MERGED = "org.spongepowered.asm.mixin.transformer.meta.MixinMerged";
    private static final String MOOGS_DATA = "com.finndog.moogs_structures.world.structures.terrainadaptation.beardifier.EnhancedBeardifierData";

    private static final boolean SKIPPABLE;
    private static final MethodHandle MOOGS_PIECES;
    private static final MethodHandle MOOGS_JUNCTIONS;
    private static volatile boolean moogsBroken;

    static {
        Set<String> foreign = new TreeSet<>();
        for (Method m : Beardifier.class.getDeclaredMethods()) {
            String mixin = mergedBy(m);
            if (mixin != null && !KNOWN_MIXINS.contains(mixin)) foreign.add(mixin);
        }
        MethodHandle pieces = null;
        MethodHandle junctions = null;
        try {
            Class<?> data = Class.forName(MOOGS_DATA, false, Beardifier.class.getClassLoader());
            if (data.isAssignableFrom(Beardifier.class)) {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                pieces = lookup.findVirtual(data, "moogs_structures_getEnhancedPieceIterator", MethodType.methodType(ObjectListIterator.class));
                junctions = lookup.findVirtual(data, "moogs_structures_getEnhancedJunctionIterator", MethodType.methodType(ObjectListIterator.class));
            }
        } catch (ClassNotFoundException e) {
        } catch (ReflectiveOperationException e) {
            foreign.add(MOOGS_DATA);
        }
        MOOGS_PIECES = pieces;
        MOOGS_JUNCTIONS = junctions;
        SKIPPABLE = foreign.isEmpty();
        if (!SKIPPABLE) TheBeyond.LOGGER.info("[Beyond] Beardifier is changed by {}, so every chunk keeps computing its beard", foreign);
    }

    private BeardSkip() {}

    static boolean empty(Beardifier beard) {
        if (!SKIPPABLE) return false;
        BeardifierAccessor acc = (BeardifierAccessor) beard;
        if (acc.the_beyond$pieces().hasNext() || acc.the_beyond$junctions().hasNext()) return false;
        if (MOOGS_PIECES == null) return true;
        if (moogsBroken) return false;
        try {
            return !hasNext(MOOGS_PIECES.invoke(beard)) && !hasNext(MOOGS_JUNCTIONS.invoke(beard));
        } catch (Throwable t) {
            moogsBroken = true;
            TheBeyond.LOGGER.warn("[Beyond] Moog's Structure Lib beard lists became unreadable, so every chunk keeps computing its beard", t);
            return false;
        }
    }

    private static boolean hasNext(Object iterator) {
        return iterator != null && ((ObjectListIterator<?>) iterator).hasNext();
    }

    private static String mergedBy(Method m) {
        for (Annotation a : m.getDeclaredAnnotations()) {
            if (!a.annotationType().getName().equals(MIXIN_MERGED)) continue;
            try {
                return (String) a.annotationType().getMethod("mixin").invoke(a);
            } catch (ReflectiveOperationException e) {
                return m.getName();
            }
        }
        return null;
    }
}
