package com.thebeyond.common.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thebeyond.TheBeyond;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Organism presets, the four built-in setups plus JSON files that extend one and override any of its keys. */
public final class OrganicPresets {
    static final Path DIR = Path.of(System.getProperty("beyond.sub.org.presets", "config/the_beyond/organic_presets"));
    public static final List<String> BUILT_IN = List.of("root", "strangler_fig", "fungus", "slime_mold");
    // what the built-ins change from the code defaults: organs bulge from the floor, and the mold also lives on it
    private static final Map<String, Map<String, String>> BUILT_IN_VALUES = Map.of(
            "root", Map.of("bulge", "dome", "bulge.height", "3.0"),
            "strangler_fig", Map.of("bulge", "dome", "bulge.height", "3.0"),
            "fungus", Map.of("bulge", "dome", "bulge.height", "3.0"),
            "slime_mold", Map.of("bulge", "dome", "bulge.height", "3.0", "substrate", "both"));
    // keys outside the growth model, read by the world rules
    static final Set<String> FEATURE_KEYS = Set.of("extends", "style", "substrate", "bulge", "bulge.height", "light.shy",
            "bulb.speed", "bulb.death", "bulb.shield", "atrophy", "zoneLine");
    private static final Set<String> SKIP = Set.of("style", "seed", "bio", "bulge", "bulgeH");
    private static volatile Map<String, Preset> presets;
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    /** A resolved preset: its base built-in, the keys it sets along its chain, and where each came from. */
    public record Preset(String name, String base, Map<String, String> values) {
        String get(String key) {
            return values.get(key);
        }
    }

    private OrganicPresets() {}

    static synchronized void reload() {
        presets = null;
        MISSING.clear();
    }

    /** Tests: the presets of this folder instead of the game's. */
    static synchronized void use(Path dir) {
        presets = load(dir);
        MISSING.clear();
    }

    static Map<String, Preset> all() {
        Map<String, Preset> p = presets;
        if (p == null) {
            synchronized (OrganicPresets.class) {
                p = presets;
                if (p == null) {
                    p = load(DIR);
                    presets = p;
                }
            }
        }
        return p;
    }

    /** The preset of this name, or the root preset when the name is unknown (a removed file keeps old worlds working). */
    static Preset get(String name) {
        Preset found = find(name);
        if (found != null) return found;
        if (MISSING.add(String.valueOf(name))) {
            TheBeyond.LOGGER.error("[Beyond] organic preset {} is missing, using root's growth", name);
        }
        return all().get("root");
    }

    // "" (the v4 setup) and unknown names keep the old per-setup defaults in the life rules, only growth falls back to root
    private static Preset find(String name) {
        if (name == null) return null;
        Map<String, Preset> p = all();
        Preset found = p.get(name);
        return found != null ? found : p.get(name.split(":", 2)[0]);
    }

    static String base(String name) {
        Preset p = find(name);
        return p == null ? name : p.base();
    }

    static Map<String, Preset> load(Path dir) {
        Map<String, Map<String, String>> raw = new TreeMap<>();
        for (String b : BUILT_IN) raw.put(b, new LinkedHashMap<>(BUILT_IN_VALUES.get(b)));
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
                for (Path f : files) {
                    String name = f.getFileName().toString().replaceFirst("\\.json$", "").toLowerCase(Locale.ROOT);
                    try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                        Map<String, String> flat = new LinkedHashMap<>();
                        flatten("", JsonParser.parseReader(r), flat);
                        if (BUILT_IN.contains(name) && !flat.containsKey("extends")) flat.put("extends", "");
                        raw.put(name, flat);
                    } catch (IOException | RuntimeException e) {
                        TheBeyond.LOGGER.error("[Beyond] organic preset {} could not be read, it is left out", f, e);
                    }
                }
            } catch (IOException e) {
                TheBeyond.LOGGER.error("[Beyond] organic preset folder {} could not be listed", dir, e);
            }
        }
        Map<String, Preset> out = new TreeMap<>();
        for (String name : raw.keySet()) {
            Preset p = resolve(name, raw, new ArrayList<>());
            if (p != null) out.put(name, p);
        }
        for (Preset p : out.values()) check(p);
        if (raw.size() > BUILT_IN.size()) {
            TheBeyond.LOGGER.info("[Beyond] organic presets: {}", String.join(", ", out.keySet()));
        }
        return Map.copyOf(out);
    }

    private static Preset resolve(String name, Map<String, Map<String, String>> raw, List<String> chain) {
        Map<String, String> own = raw.get(name);
        if (own == null || chain.contains(name)) {
            TheBeyond.LOGGER.error("[Beyond] organic preset {} {}", name, own == null ? "is missing" : "extends itself " + chain);
            return null;
        }
        chain.add(name);
        String parent = own.getOrDefault("extends", BUILT_IN.contains(name) ? "" : "root");
        Map<String, String> values = new LinkedHashMap<>();
        String base = name;
        if (!parent.isEmpty() && !parent.equals(name)) {
            Preset up = resolve(parent, raw, chain);
            if (up == null) return null;
            values.putAll(up.values());
            base = up.base();
        } else if (BUILT_IN.contains(name)) {
            values.putAll(BUILT_IN_VALUES.get(name));
        }
        for (Map.Entry<String, String> e : own.entrySet()) {
            if (!e.getKey().equals("extends")) values.put(e.getKey(), e.getValue());
        }
        return new Preset(name, base, Map.copyOf(values));
    }

    private static void flatten(String prefix, JsonElement e, Map<String, String> out) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (Map.Entry<String, JsonElement> c : o.entrySet()) {
                flatten(prefix.isEmpty() ? c.getKey() : prefix + "." + c.getKey(), c.getValue(), out);
            }
        } else if (e.isJsonPrimitive()) {
            out.put(prefix, e.getAsJsonPrimitive().getAsString());
        } else {
            throw new IllegalArgumentException("key " + prefix + " must be a number, a boolean or a word");
        }
    }

    // unknown keys are reported once with what is known, so a typo shows at boot instead of doing nothing
    private static void check(Preset p) {
        Set<String> net = netKeys(), bio = Set.of(OrganicNetwork.Bio.keys());
        for (String k : p.values().keySet()) {
            boolean ok = FEATURE_KEYS.contains(k) || (k.startsWith("net.") && net.contains(k.substring(4)))
                    || (k.startsWith("bio.") && bio.contains(k.substring(4)));
            if (!ok) {
                TheBeyond.LOGGER.error("[Beyond] organic preset {}: unknown key {} (known: {}, net.<{}>, bio.<{}>)", p.name(), k,
                        new TreeSet<>(FEATURE_KEYS), String.join("|", new TreeSet<>(net)), String.join("|", new TreeSet<>(bio)));
            }
        }
    }

    static Set<String> netKeys() {
        Set<String> out = new TreeSet<>();
        for (RecordComponent c : OrganicNetwork.Settings.class.getRecordComponents()) {
            if (!SKIP.contains(c.getName())) out.add(c.getName());
        }
        return out;
    }

    /** The growth settings of a preset: the code defaults of its base, then its net.* and bio.* keys and the bulge. */
    static OrganicNetwork.Settings settings(String name, OrganicNetwork.Style style, long seed, boolean flux,
                                            OrganicNetwork.Substrate where) {
        Preset p = get(name);
        StringBuilder bio = new StringBuilder(p.base());
        for (Map.Entry<String, String> e : p.values().entrySet()) {
            if (!e.getKey().startsWith("bio.")) continue;
            bio.append(bio.indexOf(":") < 0 ? ":" : ",").append(e.getKey().substring(4)).append('=').append(e.getValue());
        }
        OrganicNetwork.Settings base;
        try {
            base = OrganicNetwork.Settings.of(style, seed, flux, bio.toString(), where);
        } catch (IllegalArgumentException e) {
            TheBeyond.LOGGER.error("[Beyond] organic preset {}: {}, using {} as it ships", p.name(), e.getMessage(), p.base());
            base = OrganicNetwork.Settings.of(style, seed, flux, p.base(), where);
        }
        Map<String, Object> over = new HashMap<>();
        for (Map.Entry<String, String> e : p.values().entrySet()) {
            if (e.getKey().startsWith("net.")) over.put(e.getKey().substring(4), e.getValue());
        }
        String bulge = p.get("bulge");
        if (bulge != null) over.put("bulge", switch (bulge.toLowerCase(Locale.ROOT)) {
            case "none" -> "0";
            case "dome" -> "1";
            case "stalk" -> "2";
            default -> {
                TheBeyond.LOGGER.error("[Beyond] organic preset {}: bulge {} is not none, dome or stalk", p.name(), bulge);
                yield null;
            }
        });
        if (p.get("bulge.height") != null) over.put("bulgeH", p.get("bulge.height"));
        over.values().removeIf(v -> v == null);
        return over.isEmpty() ? base : with(base, over, p.name());
    }

    private static OrganicNetwork.Settings with(OrganicNetwork.Settings base, Map<String, Object> over, String preset) {
        RecordComponent[] comps = OrganicNetwork.Settings.class.getRecordComponents();
        Object[] args = new Object[comps.length];
        Class<?>[] types = new Class<?>[comps.length];
        try {
            for (int i = 0; i < comps.length; i++) {
                RecordComponent c = comps[i];
                types[i] = c.getType();
                args[i] = c.getAccessor().invoke(base);
                Object v = over.get(c.getName());
                if (v == null) continue;
                String s = v.toString().trim();
                try {
                    if (types[i] == double.class) args[i] = Double.parseDouble(s);
                    else if (types[i] == int.class) args[i] = (int) Math.round(Double.parseDouble(s));
                    else if (types[i] == boolean.class) args[i] = Boolean.parseBoolean(s);
                    else TheBeyond.LOGGER.error("[Beyond] organic preset {}: net.{} cannot be set", preset, c.getName());
                } catch (NumberFormatException e) {
                    TheBeyond.LOGGER.error("[Beyond] organic preset {}: net.{} = {} is not a number", preset, c.getName(), s);
                }
            }
            Constructor<OrganicNetwork.Settings> ctor = OrganicNetwork.Settings.class.getDeclaredConstructor(types);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            TheBeyond.LOGGER.error("[Beyond] organic preset {} could not be applied, using its defaults", preset, e);
            return base;
        }
    }

    static OrganicNetwork.Settings bulge(OrganicNetwork.Settings st, String mode) {
        Map<String, Object> over = new HashMap<>();
        over.put("bulge", switch (mode.toLowerCase(Locale.ROOT)) {
            case "dome" -> "1";
            case "stalk" -> "2";
            default -> "0";
        });
        return with(st, over, "-Dbeyond.sub.org.bulge");
    }

    static String value(String name, String key) {
        Preset p = find(name);
        return p == null ? null : p.get(key);
    }

    /** A boolean key of the preset, else the old per-setup list (by the preset's name, then its base). */
    static boolean flag(String name, String key, Set<String> legacy) {
        String v = name == null ? null : value(name, key);
        if (v != null) return Boolean.parseBoolean(v.trim());
        return name != null && (legacy.contains(name) || legacy.contains(base(name)));
    }

    /** A number key of the preset, else the old per-setup map (by name, then base), else fallback. */
    static double number(String name, String key, Map<String, Double> legacy, double fallback) {
        String v = name == null ? null : value(name, key);
        if (v != null) {
            try {
                return Double.parseDouble(v.trim());
            } catch (NumberFormatException e) {
                TheBeyond.LOGGER.error("[Beyond] organic preset {}: {} = {} is not a number", name, key, v);
            }
        }
        if (name == null) return fallback;
        Double d = legacy.get(name);
        if (d == null) d = legacy.get(base(name));
        return d == null ? fallback : d;
    }

    /** Every key a preset can set, with what each built-in gives it, for the preset authors' reference. */
    static Map<String, Map<String, String>> reference() {
        Map<String, Map<String, String>> out = new TreeMap<>();
        for (String b : BUILT_IN) {
            OrganicNetwork.Settings st = settings(b, OrganicNetwork.Style.TREE, 0, true, OrganicGrowth.substrate(b));
            for (RecordComponent c : OrganicNetwork.Settings.class.getRecordComponents()) {
                if (SKIP.contains(c.getName())) continue;
                try {
                    out.computeIfAbsent("net." + c.getName(), k -> new LinkedHashMap<>()).put(b, String.valueOf(c.getAccessor().invoke(st)));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            String[] keys = OrganicNetwork.Bio.keys();
            double[] vals = OrganicNetwork.Bio.values(b);
            for (int i = 0; i < keys.length; i++) {
                out.computeIfAbsent("bio." + keys[i], k -> new LinkedHashMap<>()).put(b, String.valueOf(vals[i]));
            }
            out.computeIfAbsent("bulge", k -> new LinkedHashMap<>()).put(b, st.bulge() == 0 ? "none" : st.bulge() == 1 ? "dome" : "stalk");
            out.computeIfAbsent("bulge.height", k -> new LinkedHashMap<>()).put(b, String.valueOf(st.bulgeH()));
            out.computeIfAbsent("substrate", k -> new LinkedHashMap<>()).put(b, OrganicGrowth.substrate(b).name().toLowerCase(Locale.ROOT));
            out.computeIfAbsent("light.shy", k -> new LinkedHashMap<>()).put(b, String.valueOf(OrganicLight.shy(b)));
            out.computeIfAbsent("bulb.speed", k -> new LinkedHashMap<>()).put(b, String.valueOf(OrganicLife.speed(b)));
            out.computeIfAbsent("bulb.death", k -> new LinkedHashMap<>()).put(b, OrganicLife.death(b).name().toLowerCase(Locale.ROOT));
            out.computeIfAbsent("bulb.shield", k -> new LinkedHashMap<>()).put(b, String.valueOf(OrganicLife.shield(b)));
            out.computeIfAbsent("atrophy", k -> new LinkedHashMap<>()).put(b, String.valueOf(OrganicGrowth.atrophies(b)));
            out.computeIfAbsent("zoneLine", k -> new LinkedHashMap<>()).put(b, String.valueOf(OrganicZoneLine.zoneLine(b)));
        }
        return out;
    }
}
