package com.thebeyond.client.model.equipment;

import com.thebeyond.TheBeyond;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.function.Consumer;
import java.util.function.Function;

// Stolen from Hekera, thanks buddy
public abstract class MultipartArmorModel {

    protected static final Consumer<PartDefinition> EMPTY_LAYER = (root) -> {};

    protected final String name;
    protected final int width;
    protected final int height;

    public MultipartArmorModel(String name, int width, int height) {

        this.name = name;
        this.width = width;
        this.height = height;
    }

    public Consumer<PartDefinition> getLayerDefinition(EquipmentSlot slot) {

        return switch (slot) {
            case FEET -> this::feetLayer;
            case LEGS -> this::legLayer;
            case CHEST -> this::chestLayer;
            case HEAD -> this::headLayer;
            case MAINHAND, OFFHAND -> EMPTY_LAYER;
            case BODY -> this::chestLayer;
        };
    }

    public ArmorModel getModelPart(EquipmentSlot slot) {

        return getModelPartConstructor(slot).apply(RenderUtils.bakeLayer(getLayerLocation(slot)));
    }

    protected Function<ModelPart, ArmorModel> getModelPartConstructor(EquipmentSlot slot) {

        return ArmorModel::new;
    }

    public ModelLayerLocation getLayerLocation(EquipmentSlot slot) {

        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, name + "_" + slot.getName()), "armor");
    }

    public String getTextureLocation(EquipmentSlot slot, String type) {

        String loc = TheBeyond.MODID + ":textures/models/armor/" + name + "/" + slot.getName();
        if (type != null) {
            loc += "_" + type;
        }
        return loc + ".png";
    }

    public int textureHeight(EquipmentSlot slot) {

        return height;
    }

    public int textureWidth(EquipmentSlot slot) {

        return width;
    }

    public void feetLayer(PartDefinition root) {

    }

    public void legLayer(PartDefinition root) {

    }

    public void chestLayer(PartDefinition root) {

    }

    public void headLayer(PartDefinition root) {

    }

}
