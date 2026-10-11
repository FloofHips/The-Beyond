package com.thebeyond.client.renderer;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

/** Wraps the reflection FBO's color texture id so a RenderType can bind it, without owning or deleting it. */
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
