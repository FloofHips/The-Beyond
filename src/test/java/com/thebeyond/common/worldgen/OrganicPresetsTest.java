package com.thebeyond.common.worldgen;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Presets: the built-ins are the code defaults plus a 3.0 dome, a file can set any key, and a broken file stops nothing. */
class OrganicPresetsTest {
    private static final OrganicNetwork.Style TREE = OrganicNetwork.Style.TREE, MESH = OrganicNetwork.Style.MESH;
    private static final OrganicNetwork.Substrate CEILING = OrganicNetwork.Substrate.CEILING;

    @AfterEach
    void reset() {
        OrganicPresets.reload();
    }

    @Test
    void builtInsAreTheCodeDefaultsWithTheBulge() throws Exception {
        OrganicPresets.use(Files.createTempDirectory("presets_none"));
        for (String b : OrganicPresets.BUILT_IN) {
            for (OrganicNetwork.Style style : OrganicNetwork.Style.values()) {
                OrganicNetwork.Settings code = OrganicNetwork.Settings.of(style, 7L, true, b, CEILING);
                OrganicNetwork.Settings preset = OrganicPresets.settings(b, style, 7L, true, CEILING);
                for (RecordComponent c : OrganicNetwork.Settings.class.getRecordComponents()) {
                    Object want = c.getName().equals("bulge") ? 1 : c.getName().equals("bulgeH") ? 3.0 : c.getAccessor().invoke(code);
                    assertEquals(want, c.getAccessor().invoke(preset), b + " " + style + " " + c.getName());
                }
            }
        }
        assertEquals(OrganicNetwork.Substrate.BOTH, OrganicGrowth.substrate("slime_mold"));
        assertEquals(CEILING, OrganicGrowth.substrate("root"));
    }

    @Test
    void aFileSetsAnyKeyAndKeepsTheRestOfItsBase() throws Exception {
        Path dir = Files.createTempDirectory("presets");
        Files.writeString(dir.resolve("meu_bolor.json"), """
                {"extends": "slime_mold", "net": {"rMax": 4.5, "rTip": 0.6, "mSub": 30}, "bio": {"kb": 0.5},
                 "substrate": "floor", "bulge": "stalk", "bulge.height": 2.2, "bulb.speed": 2, "light.shy": false}
                """);
        OrganicPresets.use(dir);
        OrganicNetwork.Settings p = OrganicPresets.settings("meu_bolor", MESH, 7L, true, OrganicGrowth.substrate("meu_bolor"));
        assertEquals(4.5, p.rMax());
        assertEquals(0.6, p.rTip());
        assertEquals(30, p.mSub());
        assertEquals(0.5, p.bio().kb());
        assertEquals(2, p.bulge());
        assertEquals(2.2, p.bulgeH());
        assertEquals(OrganicNetwork.Substrate.FLOOR, OrganicGrowth.substrate("meu_bolor"));
        assertEquals(2.0, OrganicLife.speed("meu_bolor"));
        assertFalse(OrganicLight.shy("meu_bolor"));
        assertEquals("slime_mold", OrganicPresets.base("meu_bolor"));
        OrganicNetwork.Settings slime_mold = OrganicPresets.settings("slime_mold", MESH, 7L, true, OrganicNetwork.Substrate.FLOOR);
        assertEquals(slime_mold.rCore(), p.rCore());
        assertEquals(slime_mold.bio().gUp(), p.bio().gUp());
        assertEquals(OrganicLife.death("slime_mold"), OrganicLife.death("meu_bolor"));
        assertEquals(OrganicLife.shield("slime_mold"), OrganicLife.shield("meu_bolor"));
    }

    @Test
    void aBrokenFileStopsNothingElse() throws Exception {
        Path dir = Files.createTempDirectory("presets_broken");
        Files.writeString(dir.resolve("a.json"), "{\"extends\": \"b\", \"net\": {\"rMax\": 5}}");
        Files.writeString(dir.resolve("b.json"), "{\"extends\": \"a\"}");
        Files.writeString(dir.resolve("typo.json"), "{\"extends\": \"root\", \"net\": {\"rMaxx\": 5}, \"bio\": {\"kbb\": 1}}");
        Files.writeString(dir.resolve("bad.json"), "{not json");
        Files.writeString(dir.resolve("ok.json"), "{\"extends\": \"fungus\", \"net\": {\"rMax\": 3.3}}");
        OrganicPresets.use(dir);
        assertEquals("root", OrganicPresets.get("a").name());
        assertEquals("root", OrganicPresets.get("bad").name());
        assertEquals(3.3, OrganicPresets.settings("ok", MESH, 7L, true, CEILING).rMax());
        assertEquals(OrganicPresets.settings("root", TREE, 7L, true, CEILING), OrganicPresets.settings("typo", TREE, 7L, true, CEILING));
    }

    @Test
    void writesTheKeyReference() throws Exception {
        OrganicPresets.use(Files.createTempDirectory("presets_ref"));
        Map<String, Map<String, String>> ref = OrganicPresets.reference();
        List<String> out = new ArrayList<>();
        out.add("| key | " + String.join(" | ", OrganicPresets.BUILT_IN) + " |");
        out.add("|---|" + "---|".repeat(OrganicPresets.BUILT_IN.size()));
        for (Map.Entry<String, Map<String, String>> e : ref.entrySet()) {
            List<String> v = new ArrayList<>();
            for (String b : OrganicPresets.BUILT_IN) v.add(e.getValue().getOrDefault(b, ""));
            out.add("| `" + e.getKey() + "` | " + String.join(" | ", v) + " |");
        }
        Files.write(Path.of("organic_presets.md"), out);
        assertEquals(OrganicPresets.netKeys().size() + OrganicNetwork.Bio.keys().length + 9, ref.size());
    }
}
