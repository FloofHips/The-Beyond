package com.thebeyond.compat.dt;

import java.lang.reflect.Field;
import java.util.function.Supplier;

public final class DimensionalTearsCompat {

    private static volatile Supplier<?> oceanSupplier;
    private static volatile boolean resolved;

    private DimensionalTearsCompat() {
    }

    public static boolean oceanEnabled() {
        if (!resolved) {
            synchronized (DimensionalTearsCompat.class) {
                if (!resolved) {
                    resolved = true;
                    try {
                        Field f = Class.forName("com.ordana.dimensional_tears.configs.CommonConfigs")
                                .getField("DIMENSIONAL_TEARS_OCEAN");
                        oceanSupplier = (Supplier<?>) f.get(null);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        Supplier<?> s = oceanSupplier;
        if (s == null) {
            return true;
        }
        try {
            return !(s.get() instanceof Boolean b) || b;
        } catch (Throwable t) {
            return true;
        }
    }
}
