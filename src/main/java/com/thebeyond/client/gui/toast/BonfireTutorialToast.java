package com.thebeyond.client.gui.toast;

import com.thebeyond.TheBeyond;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BonfireTutorialToast implements Toast {
    private static final ResourceLocation FRAME_1 = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/bonfire/1.png");
    private static final ResourceLocation FRAME_2 = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/bonfire/2.png");
    private static final ResourceLocation FRAME_3 = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/bonfire/3.png");

    private static final ResourceLocation[] FRAMES = {
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/bonfire/1.png"),
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/bonfire/2.png"),
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/bonfire/3.png")
    };

    public BonfireTutorialToast() {

    }
    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {

        guiGraphics.blit(getTexture(timeSinceLastVisible), 0, 0, 0, 0, this.width(), this.height(), this.width(), this.height());

        return (double)timeSinceLastVisible >= (double)5000.0F * toastComponent.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }


    @Override
    public int width() {
        return 160;
    }

    @Override
    public int height() {
        return 64;
    }

    private static @NotNull ResourceLocation getTexture(long l) {
        int index = (int) ((l / 1000L) % FRAMES.length);
        return FRAMES[index];
    }
}

