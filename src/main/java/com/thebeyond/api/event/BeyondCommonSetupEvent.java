package com.thebeyond.api.event;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;

/** Mod-bus event in Beyond's common setup, after its own compat, where addons register theirs. */
@ApiStatus.Experimental
public class BeyondCommonSetupEvent extends Event implements IModBusEvent {
}
