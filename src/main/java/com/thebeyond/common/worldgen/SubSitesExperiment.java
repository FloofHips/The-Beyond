package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The habitat's sub-site network, which decides where the organic network grows its organisms. */
public final class SubSitesExperiment {
    private static final double AMOUNT = Double.parseDouble(System.getProperty("beyond.sub.amount", "60000"));
    private static final SubSites.Outer OUTER = SubSites.Outer.valueOf(System.getProperty("beyond.sub.outer", "TREE"));
    private static final boolean IN_WORLD = Boolean.parseBoolean(System.getProperty("beyond.sub.inworld", "true"));
    private static final Map<Set<ResourceKey<Biome>>, SubSites.Network> NETWORKS = new ConcurrentHashMap<>();

    private SubSitesExperiment() {}

    static void forget() {
        NETWORKS.clear();
    }

    static SubSites.Network network(BeyondEndBiomeSource source, Set<ResourceKey<Biome>> home, int minY, int maxY) {
        return NETWORKS.computeIfAbsent(home, k -> {
            SubSites.Network[] net = new SubSites.Network[1];
            net[0] = SubSites.of().network(new SubSites.Settings(BiomeEdgeQuery.Shape.SPHERE,
                    SubSites.Count.PER_VOLUME, AMOUNT, SubSites.Inner.DELAUNAY, OUTER, 0x5B5B5B5BL,
                    p -> (!IN_WORLD || (p.getY() >= minY && p.getY() < maxY))
                            && in(source.getBiome(p.getX(), p.getY(), p.getZ()), home),
                    c -> member(source, net[0], c, home), c -> -c.y()));
            return net[0];
        });
    }

    private static final Set<Long> TRACED = ConcurrentHashMap.newKeySet();

    // The habitat's own sites inside the world decide, since the center can sit above the top or in another of the cell's biomes
    private static boolean member(BeyondEndBiomeSource source, SubSites.Network net, SubSites.Cell c,
                                  Set<ResourceKey<Biome>> home) {
        long t0 = System.nanoTime();
        int sites = net.probe(c).subs().size();
        boolean in = sites > 0;
        if (OrganicWorld.TRACE && TRACED.add(SubSites.Network.key(c))) {
            Holder<Biome> v = voidSide(source, c), s = solidSide(source, c);
            TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG habitat cell %d %d %d at %.0f %.0f %.0f | void %s solid %s"
                    + " | sites %d in %.1f ms | member %b", c.lx(), c.ly(), c.lz(), c.x(), c.y(), c.z(), name(v), name(s),
                    sites, (System.nanoTime() - t0) / 1e6, in));
        }
        return in;
    }

    private static String name(Holder<Biome> b) {
        return b.unwrapKey().map(k -> k.location().toString()).orElse("?");
    }

    // A land biome (attracta, lustrous, a foreign mod's) holds the cell's solid side, never its void side.
    private static Holder<Biome> solidSide(BeyondEndBiomeSource source, SubSites.Cell c) {
        int x = (int) Math.floor(c.x()), y = (int) Math.floor(c.y()), z = (int) Math.floor(c.z());
        Holder<Biome> fixed = source.densityFreeBiome(x, y, z);
        return fixed != null ? fixed : source.sideBiome(x, y, z, false);
    }

    static boolean in(Holder<Biome> b, Set<ResourceKey<Biome>> home) {
        return b.unwrapKey().map(home::contains).orElse(false);
    }

    private static Holder<Biome> voidSide(BeyondEndBiomeSource source, SubSites.Cell c) {
        int x = (int) Math.floor(c.x()), y = (int) Math.floor(c.y()), z = (int) Math.floor(c.z());
        Holder<Biome> fixed = source.densityFreeBiome(x, y, z);
        return fixed != null ? fixed : source.sideBiome(x, y, z, true);
    }
}
