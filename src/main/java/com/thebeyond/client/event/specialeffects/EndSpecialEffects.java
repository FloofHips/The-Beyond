package com.thebeyond.client.event.specialeffects;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class EndSpecialEffects extends DimensionSpecialEffects {

    public EndSpecialEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return biomeFogColor;

        if (level.isThundering()) {
            return biomeFogColor.subtract(0,1 * level.thunderLevel,0);
        } else if (level.isRaining()) {
            return biomeFogColor.subtract(0,0.3 * level.rainLevel,0);
        }
        return biomeFogColor;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return super.getSunriseColor(6000, partialTicks);
    }

    @Override
    public boolean isFoggyAt(int i, int i1) {
        return false;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken,
                                     float blockLightRedFlicker, float skyLight,
                                     int pixelX, int pixelY, Vector3f colors) {

        float rain = level.getRainLevel(partialTicks);
        float thunder = level.getThunderLevel(partialTicks);

        float position = Mth.clamp((float) (Minecraft.getInstance().player.position().y + 132 / 387), 0, 1);

        if (thunder > 0) {
            float time = (level.getGameTime() + partialTicks) * 0.1f;

            float red = Mth.clamp(Mth.sin(time) * 0.7f + 0.7f, 0f, 1f);
            float green = Mth.clamp(Mth.sin(time + Mth.TWO_PI/3f) * 0.3f + 0.2f, 0f, 0f);
            float blue = Mth.clamp(Mth.sin(time + Mth.TWO_PI*2f/3f) * 0.8f + 0.5f, 0f, 1f);

            colors.set(
                    Mth.lerp(thunder, colors.x(), colors.x() + red * position),
                    Mth.lerp(thunder, colors.y(), colors.y() * position),
                    Mth.lerp(thunder, colors.z(), colors.z() + blue * position)
            );
        } else if (rain > 0) {
            colors.set(
                    Mth.lerp(rain, colors.x(), colors.x() * 1.0f),
                    Mth.lerp(rain, colors.y(), colors.y() * 0.7f),
                    Mth.lerp(rain, colors.z(), colors.z() * 1.0f)
            );
        } else {
            colors.set(
                    Mth.lerp(rain, colors.x(), colors.x() * 1.7f),
                    Mth.lerp(rain, colors.y(), colors.y() * 0.5f),
                    Mth.lerp(rain, colors.z(), colors.z() * 1.7f)
            );
        }
    }
}