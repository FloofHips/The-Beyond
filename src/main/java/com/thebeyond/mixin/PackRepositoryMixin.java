package com.thebeyond.mixin;

import com.thebeyond.TheBeyond;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Auto-enables {@code beyond_terrain} datapack on world creation. Fires only when the
 * selection is exactly {@code ["vanilla"]} (fresh world default) — explicit UI disables persist.
 */
@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    private static final String BEYOND_TERRAIN_PACK_ID = "mod/" + TheBeyond.MODID + ":beyond_terrain";

    @ModifyVariable(method = "setSelected(Ljava/util/Collection;)V", at = @At("HEAD"), argsOnly = true)
    private Collection<String> the_beyond$autoEnableBeyondTerrain(Collection<String> incoming) {
        if (incoming == null) return incoming;
        // Heuristic: only augment when the selection is the bare vanilla default.
        if (incoming.size() != 1 || !incoming.contains("vanilla")) return incoming;

        PackRepository self = (PackRepository) (Object) this;
        if (!self.isAvailable(BEYOND_TERRAIN_PACK_ID)) return incoming;
        // If we're already selected somehow, no work to do.
        if (self.getSelectedIds().contains(BEYOND_TERRAIN_PACK_ID)) return incoming;

        TheBeyond.LOGGER.info("[TheBeyond] Auto-enabling {} on vanilla-only default selection",
                BEYOND_TERRAIN_PACK_ID);
        List<String> augmented = new ArrayList<>(incoming);
        augmented.add(BEYOND_TERRAIN_PACK_ID);
        return augmented;
    }
}
