package com.thebeyond.client.camera;

/** Client-only aim flag; the viewfinder layer also clears it when the camera is dropped so the overlay can't stick on. */
public final class CameraAim {
    private static boolean aiming;

    private CameraAim() {
    }

    public static boolean isAiming() {
        return aiming;
    }

    public static void set(boolean value) {
        aiming = value;
    }

    public static void clear() {
        aiming = false;
    }
}
