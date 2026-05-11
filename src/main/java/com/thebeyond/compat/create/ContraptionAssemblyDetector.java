package com.thebeyond.compat.create;

import net.neoforged.fml.ModList;

/** Stack-walk for contraption-assembly paths (Create + Sable/Aeronautics). No third-party imports. */
public final class ContraptionAssemblyDetector {
    private ContraptionAssemblyDetector() {}

    private static final String[] PREFIXES = {
            "com.simibubi.create.content.contraptions",
            "dev.ryanhcode.sable",
            "dev.eriksonn.aeronautics"
    };

    public static boolean isAssembling() {
        ModList m = ModList.get();
        if (!m.isLoaded("create") && !m.isLoaded("sable") && !m.isLoaded("aeronautics")) return false;
        return StackWalker.getInstance().walk(s -> s.anyMatch(f -> {
            String c = f.getClassName();
            for (String pre : PREFIXES) if (c.startsWith(pre)) return true;
            return false;
        }));
    }
}
