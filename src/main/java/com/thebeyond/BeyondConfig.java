package com.thebeyond;

import net.neoforged.neoforge.common.ModConfigSpec;


public class BeyondConfig {
    public static ModConfigSpec COMMON_CONFIG;
    public static ModConfigSpec CLIENT_CONFIG;
    public static final String CATEGORY_EXAMPLE = "example_category";
    public static ModConfigSpec.BooleanValue EXAMPLE;

    static {

        ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

        COMMON_CONFIG = COMMON_BUILDER.build();

        ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }

}
