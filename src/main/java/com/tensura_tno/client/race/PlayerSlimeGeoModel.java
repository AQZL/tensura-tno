package com.tensura_tno.client.race;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 直接借用主模组 {@code tensura:slime} 的 geckolib 几何/动画/纹理资源。
 *
 * <p>资源路径（已存在于 tensura.jar 内，运行时由资源管理器统一加载）：
 * <ul>
 *     <li>几何 - {@code tensura:geo/entity/slime.geo.json}</li>
 *     <li>动画 - {@code tensura:animations/entity/slime.animation.json}</li>
 *     <li>纹理 - {@code tensura:textures/entity/slime/slime.png}（天蓝色 DEFAULT 色）</li>
 * </ul>
 *
 * <p>所有 4 种 slime 种族（slime / metal_slime / demon_slime / god_slime）共用此模型，
 * 颜色固定为天蓝色。
 *
 * <p><b>RenderType</b>：与主模组 SlimeModel 一致使用 {@link RenderType#entityTranslucent}（半透明胶质感）。
 *
 * <p><b>装饰 bone 隐藏</b>：geo.json 里包含 5 个可选装饰 bone：Saddle / Chest /
 * SantaHat / HalloweenSkull / HeadArmor，都是 SlimeEntity 配合内部状态显示的。
 * 玩家身上这些状态全是默认（无鞍、无箱、无圣诞帽等），原版 SlimeModel 默认会把它们
 * 全部显示出来（因为 hidden 状态判断走 SlimeEntity 字段，对玩家而言是 false）。
 * 我们必须在每帧渲染前强制 {@code setHidden(true)} 把它们藏掉。
 */
public final class PlayerSlimeGeoModel extends GeoModel<PlayerSlimeAnimatable> {

    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(
            "tensura", "geo/entity/slime.geo.json");
    private static final ResourceLocation ANIM = ResourceLocation.fromNamespaceAndPath(
            "tensura", "animations/entity/slime.animation.json");
    /** 天蓝色史莱姆（SlimeColor.DEFAULT 对应的贴图）。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "tensura", "textures/entity/slime/slime.png");

    /** 玩家版本始终隐藏的装饰 bone。 */
    private static final String[] HIDDEN_BONES = {
            "Saddle", "Chest", "SantaHat", "HalloweenSkull", "HeadArmor"
    };

    @Override
    public ResourceLocation getModelResource(PlayerSlimeAnimatable animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(PlayerSlimeAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PlayerSlimeAnimatable animatable) {
        return ANIM;
    }

    /** 与主模组 {@code tensura:slime} 一致的半透明渲染（{@link RenderType#entityTranslucent}）。 */
    @Override
    public RenderType getRenderType(PlayerSlimeAnimatable animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    /** 每帧强制隐藏所有装饰 bone（鞍/箱/圣诞帽/万圣节头骨/头盔位）。 */
    @Override
    public void setCustomAnimations(PlayerSlimeAnimatable animatable, long instanceId,
                                    AnimationState<PlayerSlimeAnimatable> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        for (String name : HIDDEN_BONES) {
            GeoBone bone = this.getAnimationProcessor().getBone(name);
            if (bone != null && !bone.isHidden()) {
                bone.setHidden(true);
            }
        }

        if (animatable.getPlayer() != null && PlayerSlimeAnimatable.shouldKeepDefaultShape(animatable.getPlayer())) {
            GeoBone slime = this.getAnimationProcessor().getBone("slime");
            if (slime != null) {
                slime.updateScale(1.0F, 1.0F, 1.0F);
            }
        }
    }
}
