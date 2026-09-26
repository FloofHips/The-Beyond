package com.thebeyond.client.camera;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.ModClientEvents;

/** Client-only aim flag; the viewfinder layer also clears it when the camera is dropped so the overlay can't stick on. */
public final class CameraAim {
    private static boolean aiming;
    private static View view;
    private static View shot;

    public record View(double fov, float yaw, float pitch) {
    }

    private CameraAim() {
    }

    public static void recordView(double fov, float yaw, float pitch) {
        view = new View(fov, yaw, pitch);
    }

    /** What was on screen when the shutter fired, or null when nothing was recorded. */
    public static View shot() {
        return shot;
    }

    /** The photo renders a server round trip later, with the zoom gone and the mouse moved, so it keeps this view. */
    public static void shoot() {
        shot = view;
        TheBeyond.LOGGER.info("[camera] shutter {}", shot);
        clear();
    }

    public static boolean isAiming() {
        return aiming;
    }

    public static void set(boolean value) {
        aiming = value;
    }

    public static void clear() {
        aiming = false;
        view = null;
        ModClientEvents.zoomModifier = 1;
    }
}
