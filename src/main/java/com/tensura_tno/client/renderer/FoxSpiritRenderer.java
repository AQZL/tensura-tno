package com.tensura_tno.client.renderer;

import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 契约小狐灵渲染器 —— 复用原版 {@link FoxModel} 与原版狐狸贴图。
 * <p>用户后续若想替换为自制模型，只要替换本类即可，无需改动实体逻辑。
 */
public class FoxSpiritRenderer extends MobRenderer<FoxSpiritEntity, FoxModel<FoxSpiritEntity>> {

    private static final ResourceLocation FOX_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/fox/fox.png");

    public FoxSpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new FoxModel<>(context.bakeLayer(ModelLayers.FOX)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(FoxSpiritEntity entity) {
        return FOX_TEXTURE;
    }
}
