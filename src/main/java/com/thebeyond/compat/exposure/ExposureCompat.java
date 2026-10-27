package com.thebeyond.compat.exposure;

import com.thebeyond.TheBeyond;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;

/** Projector access to Exposure items, touching Exposure classes only when it is loaded. */
public final class ExposureCompat {
    public static final boolean LOADED = ModList.get().isLoaded("exposure");
    private static boolean warned;

    private ExposureCompat() {
    }

    /** A stack or album as its photographs in order, anything else as itself. */
    public static List<ItemStack> slides(ItemStack stack) {
        if (!LOADED) {
            return List.of(stack);
        }
        try {
            return ExposurePhotos.slides(stack);
        } catch (LinkageError e) {
            if (!warned) {
                warned = true;
                TheBeyond.LOGGER.warn("[Beyond] Exposure stacks and albums unreadable, the projector shows them as items", e);
            }
            return List.of(stack);
        }
    }

    public static ItemStack cover(ItemStack stack) {
        return slides(stack).get(0);
    }
}
