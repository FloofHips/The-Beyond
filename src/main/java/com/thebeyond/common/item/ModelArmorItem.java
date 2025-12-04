package com.thebeyond.common.item;

import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.client.model.equipment.ArmorModel;
import com.thebeyond.client.model.equipment.MultipartArmorModel;
import cpw.mods.util.Lazy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

// Stolen from Hekera, thanks buddy
public abstract class ModelArmorItem extends ArmorItem {

    public final Supplier<MultipartArmorModel> modelSupplier;

    public ModelArmorItem(Holder<ArmorMaterial> material, Type slot, Properties properties, Supplier<MultipartArmorModel> modelSupplier) {

        super(material, slot, properties);
        this.modelSupplier = modelSupplier;
        //ModClientEvents.addModelArmor(this);
    }

    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return getArmorModel().getTextureLocation(slot, type);
    }

    public MultipartArmorModel getArmorModel() {

        return modelSupplier.get();
    }

}
