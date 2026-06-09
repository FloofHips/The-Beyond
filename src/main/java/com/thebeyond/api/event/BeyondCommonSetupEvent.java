package com.thebeyond.api.event;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;

/** Mod-bus event in Beyond's {@code FMLCommonSetupEvent} after Beyond's own compat
 *  registration — addons register their compat modules here. */
@ApiStatus.Experimental
public class BeyondCommonSetupEvent extends Event implements IModBusEvent {
}
