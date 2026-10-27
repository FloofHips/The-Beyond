package com.thebeyond.mixin;

import com.thebeyond.common.entity.util.livingblock.LivingBlockNeighbourhood;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerLevel.class)
public abstract class ServerLevelEntitySectionsMixin implements LivingBlockNeighbourhood.Sections {
    @Shadow
    @Final
    private PersistentEntitySectionManager<Entity> entityManager;

    @SuppressWarnings("unchecked")
    @Override
    public EntitySectionStorage<Entity> the_beyond$entitySections() {
        return (EntitySectionStorage<Entity>) ((PersistentEntitySectionManagerAccessor) this.entityManager).the_beyond$sectionStorage();
    }
}
