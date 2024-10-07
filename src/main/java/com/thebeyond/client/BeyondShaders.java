package com.thebeyond.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thebeyond.TheBeyond;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class BeyondShaders {
    public static ShaderInstance translucentProximityShader;
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        ResourceProvider resourceProvider = event.getResourceProvider();
        event.registerShader(new ShaderInstance(resourceProvider, ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"translucent_proximity_shader"), DefaultVertexFormat.NEW_ENTITY), shader -> translucentProximityShader = shader);
    }
}