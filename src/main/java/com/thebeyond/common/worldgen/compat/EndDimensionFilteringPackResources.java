package com.thebeyond.common.worldgen.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * {@link PackResources} wrapper that hides {@code dimension/the_end.json} and
 * {@code dimension_type/the_end.json} from foreign packs so Beyond's pack wins those slots.
 * Everything else (biomes, noise settings, features, etc.) passes through unchanged.
 *
 * <p>Applied by {@code MultiPackResourceManagerMixin} when Beyond's pack is present.</p>
 */
public class EndDimensionFilteringPackResources implements PackResources {

    private static final ResourceLocation END_DIMENSION = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension/the_end.json");
    private static final ResourceLocation END_DIMENSION_TYPE = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension_type/the_end.json");

    /** All resources this wrapper hides from the resource manager. */
    private static final Set<ResourceLocation> HIDDEN = Set.of(END_DIMENSION, END_DIMENSION_TYPE);

    private final PackResources delegate;

    public EndDimensionFilteringPackResources(PackResources delegate) {
        this.delegate = delegate;
    }

    /** Identity check used by the mixin to avoid double-wrapping. */
    public PackResources unwrap() {
        return delegate;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type == PackType.SERVER_DATA && HIDDEN.contains(location)) {
            return null;
        }
        return delegate.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type == PackType.SERVER_DATA && "minecraft".equals(namespace)) {
            // Filter hidden resources out of any listing. We delegate the listing
            // and skip only the exact resources — other dimension files (the_nether, overworld)
            // and unrelated minecraft-namespaced resources pass through.
            delegate.listResources(type, namespace, path, (loc, supplier) -> {
                if (!HIDDEN.contains(loc)) {
                    output.accept(loc, supplier);
                }
            });
            return;
        }
        delegate.listResources(type, namespace, path, output);
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return delegate.getRootResource(elements);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    @Override
    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
        return delegate.getMetadataSection(deserializer);
    }

    @Override
    public PackLocationInfo location() {
        return delegate.location();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
