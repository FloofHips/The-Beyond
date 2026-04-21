package com.thebeyond.compat.jei;

/**
 * Class-load firewall between the mod and the JEI plugin. Holds only a {@code Runnable} —
 * safe to call from anywhere without dragging JEI onto the classloader. When JEI is absent
 * the hook never gets installed and {@link #refresh()} is a no-op. Invoked from the
 * client-side knowledge sync handler in {@code BeyondNetworking}.
 */
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
