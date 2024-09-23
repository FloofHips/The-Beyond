package com.thebeyond.common.events;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.EnderglopEntity;
import com.thebeyond.registers.BeyondEntityTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = TheBeyond.MODID, bus = EventBusSubscriber.Bus.MOD)

public class ModEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event){
        event.put(BeyondEntityTypes.ENDERGLOP.get(), EnderglopEntity.createAttributes().build());
    }
}
