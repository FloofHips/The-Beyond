package com.thebeyond.api.worldgen;

import org.jetbrains.annotations.ApiStatus;

/** Per-structure policy for hosting a foreign structure in the Beyond End: placement fitness gate
 *  plus occupancy-carve + foundation. Addons set only what they need via {@link #builder(Anchor)}; unset fields keep Beyond's defaults. */
@ApiStatus.Experimental
public final class StructureIntegrationProfile {

    /** How the structure meets the island vertically: {@code SEATED} rests on it (rejected if too much
     *  footprint would float, gets a foundation fill); {@code FLOATING} hangs in the void (e.g. a space station), rejected only on ceiling clip. */
    public enum Anchor { SEATED, FLOATING }

    private final Anchor anchor;
    private final boolean rejectUnfit;
    private final int towerRadius;
    private final int towerStep;
    private final int flushTolerance;
    private final double padRejectFraction;
    private final int floatEnvelope;
    private final boolean carve;
    private final boolean coverGroundDirt;

    private StructureIntegrationProfile(Builder b) {
        this.anchor = b.anchor;
        this.rejectUnfit = b.rejectUnfit;
        this.towerRadius = b.towerRadius;
        this.towerStep = b.towerStep;
        this.flushTolerance = b.flushTolerance;
        this.padRejectFraction = b.padRejectFraction;
        this.floatEnvelope = b.floatEnvelope;
        this.carve = b.carve;
        this.coverGroundDirt = b.coverGroundDirt;
    }

    public Anchor anchor()            { return anchor; }
    /** Run the placement fitness gate at all (false → the structure is always accepted where vanilla put it). */
    public boolean rejectUnfit()      { return rejectUnfit; }
    /** SEATED: half-extent (blocks) of the footprint support scan. */
    public int towerRadius()          { return towerRadius; }
    /** SEATED: step of the footprint support scan. */
    public int towerStep()            { return towerStep; }
    /** SEATED: a column counts as supported if solid sits within this many blocks below the floor —
     *  and the foundation fill uses this SAME window, so nothing the gate accepts as supported ends up ungrounded. */
    public int flushTolerance()       { return flushTolerance; }
    /** SEATED: reject when more than this fraction of the footprint lacks support. */
    public double padRejectFraction() { return padRejectFraction; }
    /** FLOATING: body height used only for the ceiling-clip rejection. */
    public int floatEnvelope()        { return floatEnvelope; }
    /** Build an occupancy mask and feather the islands around the structure's real footprint. */
    public boolean carve()            { return carve; }
    /** Replace the structure's OWN overworld-ground blocks (dirt family) with end_stone after it places, so
     *  a ruin authored with a dirt floor reads as End stone instead of dirt on Beyond's terrain. */
    public boolean coverGroundDirt()  { return coverGroundDirt; }

    public static Builder builder(Anchor anchor) { return new Builder(anchor); }

    @ApiStatus.Experimental
    public static final class Builder {
        private final Anchor anchor;
        private boolean rejectUnfit = true;
        private int towerRadius = 19;
        private int towerStep = 1;
        private int flushTolerance = 8;
        private double padRejectFraction = 0.50;
        private int floatEnvelope = 126;
        private boolean carve = true;
        private boolean coverGroundDirt = false;

        private Builder(Anchor anchor) {
            if (anchor == null) throw new IllegalArgumentException("anchor");
            this.anchor = anchor;
        }

        public Builder rejectUnfit(boolean v)      { this.rejectUnfit = v; return this; }
        public Builder towerRadius(int v)          { this.towerRadius = v; return this; }
        public Builder towerStep(int v)            { this.towerStep = v; return this; }
        public Builder flushTolerance(int v)       { this.flushTolerance = v; return this; }
        public Builder padRejectFraction(double v) { this.padRejectFraction = v; return this; }
        public Builder floatEnvelope(int v)        { this.floatEnvelope = v; return this; }
        public Builder carve(boolean v)            { this.carve = v; return this; }
        public Builder coverGroundDirt(boolean v)  { this.coverGroundDirt = v; return this; }

        public StructureIntegrationProfile build() { return new StructureIntegrationProfile(this); }
    }
}
