package com.thebeyond.client.renderer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public record LivingBlockSkin(ResourceLocation rim, ResourceLocation fill) {

    private static final String FOLDER = "textures/block/";
    private static final String EXTENSION = ".png";
    private static final String CTM_SUFFIX = "_ctm";

    public static LivingBlockSkin of(final String namespace, final String texture) {
        return new LivingBlockSkin(locate(namespace, texture), locate(namespace, texture + CTM_SUFFIX));
    }

    public LivingBlockSkin resolved() {
        return Minecraft.getInstance().getResourceManager().getResource(this.fill).isPresent()
                ? this
                : new LivingBlockSkin(this.rim, this.rim);
    }

    private static ResourceLocation locate(final String namespace, final String name) {
        return ResourceLocation.fromNamespaceAndPath(namespace, FOLDER + name + EXTENSION);
    }
}