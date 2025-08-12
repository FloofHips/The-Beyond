package com.thebeyond.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.specialeffects.EndSpecialEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid = TheBeyond.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ForgeClientEvents {
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event){
        event.setCanceled(true);
        event.setFogShape(FogShape.SPHERE);
        event.setFarPlaneDistance((float) Minecraft.getInstance().cameraEntity.position().y + 30);
        event.setNearPlaneDistance(15);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event){
        //event.setRed(c);
        //event.setGreen(c);
        //event.setBlue(c);
    }

}
