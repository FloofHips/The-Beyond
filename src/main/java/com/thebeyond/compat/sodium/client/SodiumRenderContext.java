package com.thebeyond.compat.sodium.client;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;

/** Spare render state swapped into the live RenderSectionManager for the camera POV, then swapped back. Sodium-only. */
public final class SodiumRenderContext {
    public SortedRenderLists renderLists;
    public int renderDistance;

    public SodiumRenderContext(int renderDistance) {
        this.renderDistance = renderDistance;
        this.renderLists = SortedRenderLists.empty();
    }
}
