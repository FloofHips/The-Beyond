package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.minecraft.network.chat.Style;

import java.util.function.UnaryOperator;

public class BeyondEnums {
    public static final EnumProxy<Rarity> REMEMBRANCE = new EnumProxy<>(
            Rarity.class, -1, TheBeyond.MODID + ":remembrance", (UnaryOperator<Style>) style -> style.withColor(0x0fd772)
    );
}
