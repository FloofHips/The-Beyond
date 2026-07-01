package com.thebeyond.client.renderer.blockentities;

/** Artist-tunable projector look knobs, kept out of the renderer's correctness epsilons. (Beam reach is ProjectorRenderer.MAX_THROW, shared with the depth-map passes.) */
public final class ProjectorTunables {
    private ProjectorTunables() {
    }

    public static final double REF_DIST = 4.0;           // throw distance at which the image is its base size
    public static final float BASE_HALF = 1.0f;          // image half-size (blocks) at REF_DIST
    public static final float MIN_SCALE = 0.5f;          // smallest the image may shrink to
    public static final float MAX_SCALE = 3.0f;          // largest the image may grow to
    public static final double LAYER_DEPTH = 0.006;      // Mix-up: separation between stacked photos along the face normal
    public static final double LAYER_FAN = 0.12;         // Mix-up: sideways fan between stacked photos (fraction of half-size)
    public static final float PROJECTION_OPACITY = 0.8f; // overall image opacity; lower lets the lit surface show through
    public static final double GRAZE_MIN = 0.05;         // faces steeper than this cosine get no projector light
    public static final float SHADOW_STRENGTH = 0.4f;    // entity-shadow darkness (per-pixel/pack path only); 0 = off
    public static final float NEAR_CUTOFF = 0.32f;       // discard pixels nearer than this so the beam never paints the hand
}
