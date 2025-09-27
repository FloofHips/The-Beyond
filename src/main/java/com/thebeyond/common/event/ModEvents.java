package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.entity.EnatiousTotemEntity;
import com.thebeyond.common.entity.EnderglopEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = TheBeyond.MODID)

public class ModEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event){
        event.put(BeyondEntityTypes.ENDERGLOP.get(), EnderglopEntity.createAttributes().build());
        event.put(BeyondEntityTypes.ENADRAKE.get(), EnadrakeEntity.createAttributes().build());
        event.put(BeyondEntityTypes.ENATIOUS_TOTEM.get(), EnatiousTotemEntity.createAttributes().build());
    }
}
