package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.blockentities.RefugeMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class BeyondMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, TheBeyond.MODID);

    public static final Supplier<MenuType<RefugeMenu>> REFUGE = MENUS.register("refuge",
            () -> IMenuTypeExtension.create(RefugeMenu::new));
}
