package com.thebeyond.client.renderer;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

/** Wraps an externally-owned GL texture id (the reflection FBO's color attachment) so a
 *  RenderType can bind it via a ResourceLocation. Does NOT own/delete the id. */
public class FboTexture extends AbstractTexture {
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public void load(ResourceManager manager) {
    }

    @Override
    public void close() {
    }
}
