package com.thebeyond.common.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** {@code region} is the fragment's sub-rect of the plate; fragments sharing a {@code composeGroup} form one picture the projector detects as complete. */
public record ProjectorTexture(ResourceLocation texture, Region region, Optional<ResourceLocation> composeGroup, float opacity) {

    /** UV sub-rect, all in [0,1]. */
    public record Region(float u0, float v0, float u1, float v1) {
        public static final Region FULL = new Region(0f, 0f, 1f, 1f);
        public static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("u0").forGetter(Region::u0),
                Codec.FLOAT.fieldOf("v0").forGetter(Region::v0),
                Codec.FLOAT.fieldOf("u1").forGetter(Region::u1),
                Codec.FLOAT.fieldOf("v1").forGetter(Region::v1)
        ).apply(i, Region::new));
    }

    public static final Codec<ProjectorTexture> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(ProjectorTexture::texture),
            Region.CODEC.optionalFieldOf("region", Region.FULL).forGetter(ProjectorTexture::region),
            ResourceLocation.CODEC.optionalFieldOf("compose_group").forGetter(ProjectorTexture::composeGroup),
            Codec.floatRange(0f, 1f).optionalFieldOf("opacity", 1f).forGetter(ProjectorTexture::opacity)
    ).apply(i, ProjectorTexture::new));
}
