package com.thebeyond.client.compat;

import com.thebeyond.TheBeyond;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public final class ExecutiveZones {
    private static final boolean LOADED = ModList.get().isLoaded("executiveorders");
    private static final boolean TRACE = Boolean.getBoolean("thebeyond.traceExecutive");
    private static final double FEVER_START = 2000.0;
    private static final double FEVER_END = 3000.0;
    private static final double FEVER_EDGE = 90.0;
    private static final double CALM_START = 9000.0;
    private static final double CALM_EDGE = 50.0;
    private static float endSkyAlpha = 1f;
    private static int tracedState = -1;

    private ExecutiveZones() {}

    public static float skyShare() {
        if (!LOADED) return 0f;
        Player player = Minecraft.getInstance().player;
        if (player == null) return 0f;
        double distance = player.position().length();
        double fever = Math.min(distance - FEVER_START, FEVER_END - distance) / FEVER_EDGE;
        double calm = (distance - CALM_START) / CALM_EDGE;
        float share = (float) Mth.clamp(Math.max(fever, calm), 0.0, 1.0);
        endSkyAlpha = share > 0f ? share : 1f;
        if (TRACE) trace(share, distance);
        return share;
    }

    public static float takeEndSkyAlpha() {
        float alpha = endSkyAlpha;
        endSkyAlpha = 1f;
        return alpha;
    }

    private static void trace(float share, double distance) {
        int state = share <= 0f ? 0 : share >= 1f ? 2 : 1;
        if (state == tracedState) return;
        tracedState = state;
        TheBeyond.LOGGER.info("[Beyond] Executive zone sky: {} at {} blocks from the origin",
                state == 0 ? "Beyond sky" : state == 1 ? "fading" : "Executive sky", (int) distance);
    }
}
