package com.thebeyond.util;

import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.*;

public class ColorUtils {

    public static SmokeColorTransitionOptions voidOptions = new SmokeColorTransitionOptions(
            new Vector3f(0.7f, 0.0f, 0.9f),
            new Vector3f(0.1f, 0.1f, 0.3f),
            1f
    );

    public static SmokeColorTransitionOptions auroraOptions = new SmokeColorTransitionOptions(
            new Vector3f(0.0f, 0.9f, 0.9f),
            new Vector3f(0.0f, 0.5f, 0.5f),
            1f
    );
    public static PixelColorTransitionOptions pixelAuroraOptions = new PixelColorTransitionOptions(
            new Vector3f(0.0f, 0.9f, 0.9f),
            new Vector3f(0.0f, 0.5f, 0.5f),
            1f
    );
    public static SimplexNoise noise = new SimplexNoise(new XoroshiroRandomSource(69420));

    public static float tri(float f) {
        return Math.abs(Mth.frac(f) - 0.5F) * 2.0F;
    }

    public static int getNoiseColor(BlockPos pos, Vec3 color1, Vec3 color2, Vec3 color3, Vec3 color4, Vec3 color5){
        double NoiseValue = (tri((0.01F * pos.getX() + 0.032F * pos.getY() + 0.012F * pos.getZ()) +
                ((float) noise.getValue(pos.getX() * 0.01F, pos.getY() * 0.01F, pos.getZ() * 0.01F)) *
                        ((float) noise.getValue(pos.getX() * 0.005F, pos.getY() * 0.005F, pos.getZ() * 0.005F))));

        double XRLimit = 1.0;
        double XLLimit = 0.5;

        Vec3 RightLimit = color5;
        Vec3 LeftLimit = color1;

        if (NoiseValue>=(0) && NoiseValue<=(0.22)) {
            XRLimit = 0.22;
            XLLimit = 0.0;

            RightLimit = color2;
            LeftLimit = color1;
        }
        else if (NoiseValue>(0.22) && NoiseValue<=(0.5)) {
            XRLimit = 0.5;
            XLLimit = 0.22;

            RightLimit = color3;
            LeftLimit = color2;
        }
        else if (NoiseValue>(0.5) && NoiseValue<=(0.72)) {
            XRLimit = 0.72;
            XLLimit = 0.5;

            RightLimit = color4;
            LeftLimit = color3;
        }
        else if (NoiseValue>(0.72) && NoiseValue<=(1)) {
            XRLimit = 1.0;
            XLLimit = 0.72;

            RightLimit = color5;
            LeftLimit = color4;
        }

        int Rvalue = (int) (((NoiseValue - XRLimit) * (LeftLimit.x - RightLimit.x)) / (XLLimit - XRLimit) + RightLimit.x);
        int Gvalue = (int) (((NoiseValue - XRLimit) * (LeftLimit.y - RightLimit.y)) / (XLLimit - XRLimit) + RightLimit.y);
        int Bvalue = (int) (((NoiseValue - XRLimit) * (LeftLimit.z - RightLimit.z)) / (XLLimit - XRLimit) + RightLimit.z);

        return (Rvalue) << 16 | (Gvalue) << 8 | (Bvalue);
    }
}
