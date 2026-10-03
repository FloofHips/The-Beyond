package com.thebeyond.compat.jei;

/** Holds only a Runnable so refresh() works without JEI loaded, a no-op when JEI is absent. */
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
