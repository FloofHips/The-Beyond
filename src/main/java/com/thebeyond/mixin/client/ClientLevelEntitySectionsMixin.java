package com.thebeyond.mixin.client;

import com.thebeyond.common.entity.util.livingblock.LivingBlockNeighbourhood;
import com.thebeyond.mixin.TransientEntitySectionManagerAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientLevel.class)
public abstract class ClientLevelEntitySectionsMixin implements LivingBlockNeighbourhood.Sections {
    @Shadow
    @Final
    private TransientEntitySectionManager<Entity> entityStorage;

    @SuppressWarnings("unchecked")
    @Override
    public EntitySectionStorage<Entity> the_beyond$entitySections() {
        return (EntitySectionStorage<Entity>) ((TransientEntitySectionManagerAccessor) this.entityStorage).the_beyond$sectionStorage();
    }
}
