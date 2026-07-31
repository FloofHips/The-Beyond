package com.thebeyond.common.camera;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Keys, built-in ids, and resolution helpers for the data-driven snapshot-filter registry {@code the_beyond:grade}. */
public final class Grades {
    public static final ResourceKey<Registry<Grade>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "grade"));

    public static final ResourceLocation NONE = id("none");
    public static final ResourceLocation SEPIA = id("sepia");
    public static final ResourceLocation BLUE = id("blue");

    /** Sentinel id (NOT a registry entry): the projector defers to each photo's own grade. */
    public static final ResourceLocation AS_PHOTO = id("as_photo");

    /** Passthrough, used when an id is missing or the registry is not yet available. */
    private static final Grade FALLBACK = new Grade(new int[0][], 0f);

    /** A missing glass_<dye> palette resolves to passthrough, not a crash. */
    private static final Map<DyeColor, ResourceLocation> GLASS_GRADES = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor c : DyeColor.values()) {
            GLASS_GRADES.put(c, id("glass_" + c.getSerializedName()));
        }
    }

    private Grades() {
    }

    /** Stained-glass blocks and panes both implement BeaconBeamBlock; null for anything without a dye. */
    @Nullable
    public static ResourceLocation glassGradeId(BlockState state) {
        return state.getBlock() instanceof BeaconBeamBlock beam ? GLASS_GRADES.get(beam.getColor()) : null;
    }

    /** The grade a photo carries ({@code SNAPSHOT_GRADE}), defaulting to {@link #SEPIA} when unset. */
    public static ResourceLocation photoGrade(ItemStack stack) {
        ResourceLocation g = stack.get(BeyondComponents.SNAPSHOT_GRADE.get());
        return g != null ? g : NONE;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, path);
    }

    /** Resolves a grade id to its data, or a passthrough fallback. AS_PHOTO is a sentinel and resolves to passthrough here. */
    public static Grade resolve(RegistryAccess access, ResourceLocation gradeId) {
        if (access == null || gradeId == null) {
            return FALLBACK;
        }
        Grade g = access.registryOrThrow(REGISTRY).get(gradeId);
        return g != null ? g : FALLBACK;
    }

}
