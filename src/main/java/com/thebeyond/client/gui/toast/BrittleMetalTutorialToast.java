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

public class BrittleMetalTutorialToast implements Toast {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/brittle_metal_casting/bg.png");
    private static final ResourceLocation CLICK = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/brittle_metal_casting/click_overlay.png");
    private static final ResourceLocation NO_CLICK = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/brittle_metal_casting/no_click_overlay.png");

    private static final ResourceLocation[] TOOL_TEXTURES = {
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/brittle_metal_casting/axe.png"),
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/brittle_metal_casting/pickaxe.png"),
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/brittle_metal_casting/shovel.png"),
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/brittle_metal_casting/hoe.png"),
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/toast/brittle_metal_casting/sword.png")
    };

    public Component title;
    public Component desc;
    public BrittleMetalTutorialToast(Component title, Component desc) {
        this.title = title;
        this.desc = desc;
    }
    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {

        guiGraphics.blit(BG, 0, 0, 0, 0, this.width(), this.height(), this.width(), this.height());
        guiGraphics.blit(getToolTexture(timeSinceLastVisible), 3, 3, 0, 0, 66, 46, 66, 46);
        guiGraphics.blit(getMouseTexture(timeSinceLastVisible), 3, 3, 0, 0, 66, 46, 66, 46);



        List<FormattedCharSequence> list = toastComponent.getMinecraft().font.split(desc, 122);
        int titleColor = -12829636;
        int descColor  = -10855846;
        int titleColorFade = titleColor & 0x00FFFFFF;
        int descColorFade  = descColor  & 0x00FFFFFF;

        int x = 73;
        if (list.size() == 1) {
            guiGraphics.drawString(toastComponent.getMinecraft().font, title, x, 7, titleColor | -16777216, false);
            guiGraphics.drawString(toastComponent.getMinecraft().font, (FormattedCharSequence)list.get(0), x, 18, descColor, false);
        } else {
            int j = 1500;
            float f = 300.0F;
            if (timeSinceLastVisible < 1500L) {
                int k = Mth.floor(Mth.clamp((float)(1500L - timeSinceLastVisible) / 300.0F, 0.0F, 1.0F) * 255.0F) << 24 | 67108864;
                guiGraphics.drawString(toastComponent.getMinecraft().font, title, x, 7, titleColorFade | k, false);
            } else {
                int i1 = Mth.floor(Mth.clamp((float)(timeSinceLastVisible - 1500L) / 300.0F, 0.0F, 1.0F) * 252.0F) << 24 | 67108864;
                int l = this.height() / 2 - list.size() * 9 / 2;

                for(FormattedCharSequence formattedcharsequence : list) {
                    guiGraphics.drawString(toastComponent.getMinecraft().font, formattedcharsequence, x, l, descColorFade | i1, false);
                    l += 9;
                }
            }
        }

        return (double)timeSinceLastVisible >= (double)5000.0F * toastComponent.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;

    }



    @Override
    public int width() {
        return 200;
    }

    @Override
    public int height() {
        return 52;
    }

    public ResourceLocation getMouseTexture(long l) {
        return (l / 500L) % 2 == 0 ? CLICK : NO_CLICK;
    }

    private static @NotNull ResourceLocation getToolTexture(long l) {
        int index = (int) ((l / 1000L) % TOOL_TEXTURES.length);
        return TOOL_TEXTURES[index];
    }
}
