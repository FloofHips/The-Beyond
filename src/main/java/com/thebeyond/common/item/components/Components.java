package com.thebeyond.common.item.components;

public class Components {
    //public record ColorComponent(float red, float green, float blue, float roffset, float goffset, float boffset) {}
    //public record AlphaComponent(float alpha, float aoffset) {}
    //public record BrightnessComponent(int brightness) {}

    public record DynamicColorComponent (float red, float green, float blue, float alpha, float roffset, float goffset, float boffset, float aoffset, int brightness) {}
}



