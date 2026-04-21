package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.*;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@EventBusSubscriber(modid = TheBeyond.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event){
        event.put(BeyondEntityTypes.ENDERGLOP.get(), EnderglopEntity.createAttributes().build());
        event.put(BeyondEntityTypes.ENADRAKE.get(), EnadrakeEntity.createAttributes().build());
        event.put(BeyondEntityTypes.ENATIOUS_TOTEM.get(), EnatiousTotemEntity.createAttributes().build());
        event.put(BeyondEntityTypes.LANTERN.get(), LanternEntity.createAttributes().build());
        event.put(BeyondEntityTypes.ABYSSAL_NOMAD.get(), AbyssalNomadEntity.createAttributes().build());
    }

    /** Registered on both sides; a Dist.CLIENT-only registration would skip dedicated servers. */
    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                BeyondEntityTypes.LANTERN.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LanternEntity::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR
        );

        event.register(
                BeyondEntityTypes.ABYSSAL_NOMAD.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbyssalNomadEntity::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR
        );
    }
}
