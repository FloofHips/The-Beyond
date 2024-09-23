package com.thebeyond.common.events;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.EnderglopEntity;
import com.thebeyond.registers.RegisterEntities;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = TheBeyond.MODID, bus = EventBusSubscriber.Bus.MOD)

public class ModEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event){
        event.put(RegisterEntities.ENDERGLOP.get(), EnderglopEntity.createAttributes().build());
    }
}
