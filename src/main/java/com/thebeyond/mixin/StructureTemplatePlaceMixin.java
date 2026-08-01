package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thebeyond.api.worldgen.ForeignStructureWrite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;

/** Arms the carve veto around template writes, covering a foreign {@code .nbt} placed by a feature. */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplatePlaceMixin {

    @WrapMethod(method = "placeInWorld")
    private boolean the_beyond$scopeTemplateWrites(
            ServerLevelAccessor serverLevel, BlockPos offset, BlockPos pos, StructurePlaceSettings settings,
            RandomSource random, int flags, Operation<Boolean> original) {
        ForeignStructureWrite.enter();
        try {
            return original.call(serverLevel, offset, pos, settings, random, flags);
        } finally {
            ForeignStructureWrite.exit();
        }
    }
}
