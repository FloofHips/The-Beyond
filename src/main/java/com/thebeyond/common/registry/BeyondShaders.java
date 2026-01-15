package com.thebeyond.common.registry;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thebeyond.TheBeyond;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import javax.annotation.Nullable;

public class BeyondShaders {
    private static ShaderInstance ENTITY_DEPTH_SHADER;

    @Nullable
    public static ShaderInstance getRenderTypeDepthOverlay() {
        return ENTITY_DEPTH_SHADER;
    }

    public static void setRenderTypeDepthOverlay(ShaderInstance instance) {
        ENTITY_DEPTH_SHADER = instance;
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "rendertype_entity_depth"),
                            DefaultVertexFormat.NEW_ENTITY),
                    BeyondShaders::setRenderTypeDepthOverlay);
        } catch (Exception exception) {
            TheBeyond.LOGGER.error("The Beyond could not register internal shaders! :(", exception);
        }
    }
}
