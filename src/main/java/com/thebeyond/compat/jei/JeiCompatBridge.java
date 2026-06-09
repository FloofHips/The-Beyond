package com.thebeyond.compat.jei;

/** Class-load firewall: holds only a {@code Runnable} so {@link #refresh()} stays callable
 *  without JEI on the classloader. Hook never installed when JEI absent → no-op. */
public final class JeiCompatBridge {

    private JeiCompatBridge() {}

    private static volatile Runnable refreshHook;

    public static void setRefreshHook(Runnable hook) { refreshHook = hook; }

    /** Client-thread only. No-op until {@link BeyondJeiPlugin} installs the hook. */
    public static void refresh() {
        Runnable h = refreshHook;
        if (h != null) h.run();
    }
}
