package com.thebeyond.api.worldgen;

import org.jetbrains.annotations.ApiStatus;

/** How a foreign structure is hosted in the Beyond End: fitness gate, carve and foundation, unset fields keep defaults. */
@ApiStatus.Experimental
public final class StructureIntegrationProfile {

    /** SEATED rests on the island with a foundation, FLOATING hangs in the void and is rejected only on a ceiling clip. */
    public enum Anchor { SEATED, FLOATING }

    private final Anchor anchor;
    private final boolean rejectUnfit;
    private final int towerRadius;
    private final boolean towerRadiusPinned;
    private final int towerStep;
    private final int flushTolerance;
    private final double padRejectFraction;
    private final int floatEnvelope;
    private final boolean carve;
    private final boolean connectDetached;
    private final boolean basePedestal;
    private final boolean selfHollowing;
    private final boolean coverGroundDirt;

    private StructureIntegrationProfile(Builder b) {
        this.anchor = b.anchor;
        this.rejectUnfit = b.rejectUnfit;
        this.towerRadius = b.towerRadius;
        this.towerRadiusPinned = b.towerRadiusPinned;
        this.towerStep = b.towerStep;
        this.flushTolerance = b.flushTolerance;
        this.padRejectFraction = b.padRejectFraction;
        this.floatEnvelope = b.floatEnvelope;
        this.carve = b.carve;
        this.connectDetached = b.connectDetached;
        this.basePedestal = b.basePedestal;
        this.selfHollowing = b.selfHollowing;
        this.coverGroundDirt = b.coverGroundDirt;
    }

    public Anchor anchor()            { return anchor; }
    public boolean rejectUnfit()      { return rejectUnfit; }
    public int towerRadius()          { return towerRadius; }
    public boolean towerRadiusPinned() { return towerRadiusPinned; }
    public int towerStep()            { return towerStep; }
    public int flushTolerance()       { return flushTolerance; }
    public double padRejectFraction() { return padRejectFraction; }
    public int floatEnvelope()        { return floatEnvelope; }
    public boolean carve()            { return carve; }
    public boolean connectDetached()  { return connectDetached; }

    public boolean basePedestal()     { return basePedestal; }

    public boolean selfHollowing()    { return selfHollowing; }
    public boolean coverGroundDirt()  { return coverGroundDirt; }

    public static Builder builder(Anchor anchor) { return new Builder(anchor); }

    @ApiStatus.Experimental
    public static final class Builder {
        private final Anchor anchor;
        private boolean rejectUnfit = true;
        private int towerRadius = 19;
        private boolean towerRadiusPinned = false;
        private int towerStep = 1;
        private int flushTolerance = 8;
        private double padRejectFraction = 0.50;
        private int floatEnvelope = 126;
        private boolean carve = true;
        private boolean connectDetached = false;
        private boolean basePedestal = false;
        private boolean selfHollowing = false;
        private boolean coverGroundDirt = false;

        private Builder(Anchor anchor) {
            if (anchor == null) throw new IllegalArgumentException("anchor");
            this.anchor = anchor;
        }

        public Builder rejectUnfit(boolean v)      { this.rejectUnfit = v; return this; }
        public Builder towerRadius(int v)          { this.towerRadius = v; this.towerRadiusPinned = true; return this; }
        public Builder towerStep(int v)            { this.towerStep = v; return this; }
        public Builder flushTolerance(int v)       { this.flushTolerance = v; return this; }
        public Builder padRejectFraction(double v) { this.padRejectFraction = v; return this; }
        public Builder floatEnvelope(int v)        { this.floatEnvelope = v; return this; }
        public Builder carve(boolean v)            { this.carve = v; return this; }
        public Builder connectDetached(boolean v)  { this.connectDetached = v; return this; }
        public Builder basePedestal(boolean v)     { this.basePedestal = v; return this; }
        public Builder selfHollowing(boolean v)    { this.selfHollowing = v; return this; }
        public Builder coverGroundDirt(boolean v)  { this.coverGroundDirt = v; return this; }

        public StructureIntegrationProfile build() { return new StructureIntegrationProfile(this); }
    }
}
