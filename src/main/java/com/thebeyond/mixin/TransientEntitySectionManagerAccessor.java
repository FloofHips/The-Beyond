package com.thebeyond.mixin;

import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TransientEntitySectionManager.class)
public interface TransientEntitySectionManagerAccessor {
    @Accessor("sectionStorage")
    EntitySectionStorage<?> the_beyond$sectionStorage();
}
