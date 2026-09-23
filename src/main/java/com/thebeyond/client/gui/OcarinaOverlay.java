package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.TrinketEntity;
import com.thebeyond.common.item.OcarinaItem;
import com.thebeyond.common.item.PrismographBlockItem;
import com.thebeyond.util.OcarinaMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class OcarinaOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/ocarina/frame.png");
    private static final ResourceLocation CONTAINER = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/ocarina/container.png");

    private static final ResourceLocation SELECT = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/ocarina/select.png");
    private static final ResourceLocation FOLLOW = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/ocarina/follow.png");
    private static final ResourceLocation GUIDE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/ocarina/guide.png");
    private static final ResourceLocation SCATTER = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/ocarina/scatter.png");

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.options.hideGui) return;
        ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        ItemStack ocarina = null;
        if (isOcarina(hand)) {
            ocarina = hand;
        } else if (isOcarina(offHand)) {
            ocarina = offHand;
        }

        if (ocarina == null) {
            return;
        }

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int screenWidth= mc.getWindow().getGuiScaledWidth();

        guiGraphics.blit(FRAME, screenWidth/2 + 91 , screenHeight - 22, 0, 0, 30, 20, 30,20);

        OcarinaItem ocarinaItem = (OcarinaItem) ocarina.getItem();
        List<TrinketEntity> list = ocarinaItem.getLinkedTrinkets();

        guiGraphics.blit(getTexture(ocarina), screenWidth/2 + 98 , screenHeight - 20, 0, 0, 16, 16, 16,16);

        if (list.isEmpty()) return;
        int count = Math.min(list.size(), 5);

        for (int i = 0; i < count; i++) {
            TrinketEntity trinket = list.get(i);
            int x = screenWidth / 2 + 91 + 31 + 18 * i;
            guiGraphics.blit(CONTAINER, x, screenHeight - 20, 0, 0, 16, 16, 16,16);
            InventoryScreen.renderEntityInInventoryFollowsAngle(
                    guiGraphics,
                    x, (int) (screenHeight - 20  + ((Math.sin(System.currentTimeMillis() * 0.001 + i)) * 2)),
                    x+16, (int) (screenHeight - 4 + ((Math.sin(System.currentTimeMillis() * 0.001 + i)) * 2)),
                    12, 0,
                    0, 0, trinket
            );
        }

        int remaining = list.size() - 5;
        if (remaining > 0) {
            Component text = Component.literal("+" + remaining);
            int textY = screenHeight - 15;
            int textX = (screenWidth / 2 + 91 + 31 + 18 * count) + 3;
            guiGraphics.drawString(mc.font, text, textX, textY, 0xFFFFFF, true);
        }
    }

    private static boolean isOcarina(ItemStack ocarina) {
        return ocarina.getItem() instanceof OcarinaItem;
    }

    private static ResourceLocation getTexture(ItemStack ocarina) {
        OcarinaMode mode = OcarinaItem.getMode(ocarina);
        if (mode == null) return SELECT;
        if (mode == OcarinaMode.GUIDE) {
            return GUIDE;
        }
        if (mode == OcarinaMode.FOLLOW) {
            return FOLLOW;
        }
        if (mode == OcarinaMode.SELECT) {
            return SELECT;
        }
        if (mode == OcarinaMode.SCATTER) {
            return SCATTER;
        }
        return SELECT;
    }
}
