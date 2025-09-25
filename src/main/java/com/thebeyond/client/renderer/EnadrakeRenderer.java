package com.thebeyond.client.renderer;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.EnadrakeModel;
import com.thebeyond.client.model.EnderdropModel;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class EnadrakeRenderer extends MobRenderer<EnadrakeEntity, EnadrakeModel<EnadrakeEntity>> {
    private static final ResourceLocation ENADRAKE_TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enadrake.png");
    public EnadrakeRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new EnadrakeModel<>(pContext.bakeLayer(BeyondModelLayers.ENADRAKE)),0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(EnadrakeEntity enadrakeEntity) {
        return ENADRAKE_TEXTURE;
    }
}
