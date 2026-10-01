package com.thebeyond.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ToastManager {
    private ToastManager() {}

    public static void showBrittleMetalTutorialToast() {
        Minecraft mc = Minecraft.getInstance();
        ToastComponent toastManager = mc.getToasts();

        if (toastManager.getToast(BrittleMetalTutorialToast.class, Toast.NO_TOKEN) == null) {
            Toast t = new BrittleMetalTutorialToast(
                    Component.translatable("tooltip.block.the_beyond.brittle_metal.title"),
                    Component.translatable("tooltip.block.the_beyond.brittle_metal.desc")
            );
            toastManager.addToast(t);
        }
    }
}
