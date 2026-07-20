package com.tensura_tno.client.race;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/** Resource bridge for the nine-tailed fox-spirit player model. */
public final class PlayerFoxSpiritGeoModel extends GeoModel<PlayerFoxSpiritAnimatable> {

    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "geo/entity/player_fox_spirit.geo.json");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "animations/entity/player_fox_spirit.animation.json");
    private static final ResourceLocation PRONE_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "animations/entity/player_fox_spirit_prone.animation.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/entity/player_fox_spirit.png");

    private static final String[] DISABLED_TAILS = {
            "tail_01_base", "tail_02_base", "tail_03_base", "tail_04_base",
            "tail_06_base", "tail_07_base", "tail_08_base", "tail_09_base"
    };

    @Override
    public ResourceLocation getModelResource(PlayerFoxSpiritAnimatable animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(PlayerFoxSpiritAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PlayerFoxSpiritAnimatable animatable) {
        return animatable.getPlayer() != null
                && PlayerFoxSpiritAnimatable.shouldProne(animatable.getPlayer())
                ? PRONE_ANIMATION
                : ANIMATION;
    }

    @Override
    public RenderType getRenderType(PlayerFoxSpiritAnimatable animatable, ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture, false);
    }

    @Override
    public void setCustomAnimations(PlayerFoxSpiritAnimatable animatable, long instanceId,
                                    AnimationState<PlayerFoxSpiritAnimatable> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // Keep the complete fifth tail branch and disable the other eight.
        for (String boneName : DISABLED_TAILS) {
            GeoBone bone = this.getAnimationProcessor().getBone(boneName);
            if (bone != null) bone.setHidden(true);
        }
        GeoBone fifthTail = this.getAnimationProcessor().getBone("tail_05_base");
        if (fifthTail != null) fifthTail.setHidden(false);
    }
}
