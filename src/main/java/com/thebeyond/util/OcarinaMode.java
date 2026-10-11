package com.thebeyond.util;

import net.minecraft.network.chat.Component;

public enum OcarinaMode {
    SELECT,
    GUIDE,
    FOLLOW,
    SCATTER;

    public OcarinaMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Component displayName() {
        return Component.translatable("item.the_beyond.ocarina.mode." + name().toLowerCase());
    }

    public static OcarinaMode fromInt(int i) {
        return switch (i) {
            case 1 -> GUIDE;
            case 2 -> FOLLOW;
            case 3 -> SCATTER;
            default -> SELECT;
        };
    }

    public static int toInt(OcarinaMode i) {
        return switch (i) {
            case GUIDE -> 1;
            case FOLLOW -> 2;
            case SCATTER -> 3;
            default -> 0;
        };
    }
}
