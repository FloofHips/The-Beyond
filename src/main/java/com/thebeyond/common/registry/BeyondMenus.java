package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.menu.MemoryBankMenu;
import com.thebeyond.client.menu.PrismographMenu;
import com.thebeyond.client.menu.ProjectorMenu;
import com.thebeyond.client.menu.RefugeMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class BeyondMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, TheBeyond.MODID);

    public static final Supplier<MenuType<RefugeMenu>> REFUGE = MENUS.register("refuge",
            () -> IMenuTypeExtension.create(RefugeMenu::new));

    public static final Supplier<MenuType<ProjectorMenu>> PROJECTOR = MENUS.register("projector",
            () -> IMenuTypeExtension.create(ProjectorMenu::new));

    public static final Supplier<MenuType<PrismographMenu>> PRISMOGRAPH = MENUS.register("prismograph",
            () -> IMenuTypeExtension.create(PrismographMenu::new));

    public static final Supplier<MenuType<MemoryBankMenu>> MEMORY_BANK = MENUS.register("memory_bank",
            () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new MemoryBankMenu(id, inv, ItemStack.STREAM_CODEC.decode(buf))
            ));
}
