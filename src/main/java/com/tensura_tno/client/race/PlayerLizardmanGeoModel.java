package com.tensura_tno.client.race;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Uses a player-adapted lizardman geometry and merged animation resource for
 * race rendering.
 */
public final class PlayerLizardmanGeoModel extends GeoModel<PlayerLizardmanAnimatable> {

    private static final float DEG_TO_RAD = (float)Math.PI / 180.0F;

    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "geo/entity/player_lizardman.geo.json");
    private static final ResourceLocation ANIM = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "animations/entity/player_lizardman.animation.json");

    private static final ResourceLocation[] BODY_TEXTURES = {
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/lizardman/lizardman_green.png"),
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/lizardman/lizardman_blue.png"),
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/lizardman/lizardman_purple.png"),
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/lizardman/lizardman_yellow.png"),
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/lizardman/lizardman_red.png"),
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/lizardman/lizardman_dark_green.png")
    };

    private static final String[] HIDDEN_BONES = {
            "wings", "leftWing", "rightWing", "hair",
            "headArmor", "bodyArmor", "rightArmArmor", "leftArmArmor",
            "leftLegArmor", "rightLegArmor", "leftBootArmor", "rightBootArmor"
    };

    @Override
    public ResourceLocation getModelResource(PlayerLizardmanAnimatable animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(PlayerLizardmanAnimatable animatable) {
        Player player = animatable.getPlayer();
        if (player == null) return BODY_TEXTURES[0];
        int index = Math.floorMod(player.getUUID().hashCode(), BODY_TEXTURES.length);
        return BODY_TEXTURES[index];
    }

    @Override
    public ResourceLocation getAnimationResource(PlayerLizardmanAnimatable animatable) {
        return ANIM;
    }

    @Override
    public RenderType getRenderType(PlayerLizardmanAnimatable animatable, ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture, false);
    }

    @Override
    public void setCustomAnimations(PlayerLizardmanAnimatable animatable, long instanceId,
                                    AnimationState<PlayerLizardmanAnimatable> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        hideEntityOnlyBones();
        rotateHead(animatable.getPlayer());
    }

    private void hideEntityOnlyBones() {
        for (String name : HIDDEN_BONES) {
            GeoBone bone = this.getAnimationProcessor().getBone(name);
            if (bone != null && !bone.isHidden()) {
                bone.setHidden(true);
            }
        }
    }

    private void rotateHead(Player player) {
        if (player == null) return;
        GeoBone head = this.getAnimationProcessor().getBone("head");
        if (head == null) return;

        float netHeadYaw = Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot);
        head.setRotY(-Mth.clamp(netHeadYaw, -75.0F, 75.0F) * DEG_TO_RAD);
        head.setRotX(-player.getXRot() * DEG_TO_RAD);
    }
}
