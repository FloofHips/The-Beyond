package com.thebeyond.mixin;

import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JigsawStructure.class)
public interface JigsawStructureAccessor {
    @Accessor("maxDistanceFromCenter")
    int the_beyond$maxDistanceFromCenter();
}
