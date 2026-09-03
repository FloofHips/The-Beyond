package com.thebeyond.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class MathHelpers {
    public static Vec3 vecLerp(float progress, Vec3 from, Vec3 to) {
        float x = (float) Mth.lerp(progress, from.x, to.x);
        float y = (float) Mth.lerp(progress, from.y, to.y);
        float z = (float) Mth.lerp(progress, from.z, to.z);

        return new Vec3(x, y, z);
    }
}
