package com.thebeyond.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class BeyondTooltips {
    private BeyondTooltips() {
    }

    public static Component header(String key) {
        return line(key, ChatFormatting.GRAY);
    }

    public static Component line(String key, ChatFormatting color) {
        return Component.translatable("tooltip.the_beyond." + key).withStyle(color);
    }

    public static Component effect(String key, ChatFormatting color) {
        return CommonComponents.space().append(Component.translatable("tooltip.the_beyond." + key)).withStyle(color);
    }
}
