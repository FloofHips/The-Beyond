package com.thebeyond.compat.sodium.client;

/** Mixed into Sodium's {@code RenderSectionManager} to swap its render-list + distance. */
public interface ISodiumSectionManagerSwap {
    void the_beyond$swapContext(SodiumRenderContext context);
}
