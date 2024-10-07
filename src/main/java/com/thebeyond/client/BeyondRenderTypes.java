package com.thebeyond.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.MapCodec;
import com.thebeyond.TheBeyond;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterNamedRenderTypesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import static net.minecraft.client.renderer.RenderStateShard.*;

@EventBusSubscriber(modid = TheBeyond.MODID, bus = EventBusSubscriber.Bus.MOD)
public class BeyondRenderTypes {
    public static final RenderStateShard.ShaderStateShard TRANSLUCENT_PROXIMITY_SHADER = new RenderStateShard.ShaderStateShard(() -> BeyondShaders.translucentProximityShader);

    @SubscribeEvent
    public static void onRegisterNamedRenderTypes(RegisterNamedRenderTypesEvent event) {

        RenderType TRANSLUCENT_PROXIMITY = RenderType.create(TheBeyond.MODID + ":" +"translucent_proximity", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, true, true, RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_SOLID_SHADER)
                        .setTextureState(BLOCK_SHEET)
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true));

        event.register(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "translucent_proximity"), RenderType.translucent(), TRANSLUCENT_PROXIMITY);

    }

}
