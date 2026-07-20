package com.tensura_tno.client.race;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Player-adapted model wrapper for the regular Tensura orc entity model.
 * All orc-family player races intentionally share tensura:orc visuals.
 */
public final class PlayerOrcGeoModel extends GeoModel<PlayerOrcAnimatable> {

    private static final float DEG_TO_RAD = (float)Math.PI / 180.0F;

    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(
            "tensura", "geo/entity/orc.geo.json");
    private static final ResourceLocation ANIM = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "animations/entity/player_orc.animation.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "tensura", "textures/entity/orc/orc_ham.png");

    private static final String[] HIDDEN_BONES = {
            "HeadArmor", "ChestArmor", "RightArmArmor", "LeftArmArmor",
            "RightLegArmor", "LeftLegArmor", "RightBootArmor", "LeftBootArmor",
            "ArmorBelly"
    };

    @Override
    public ResourceLocation getModelResource(PlayerOrcAnimatable animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(PlayerOrcAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PlayerOrcAnimatable animatable) {
        return ANIM;
    }

    @Override
    public RenderType getRenderType(PlayerOrcAnimatable animatable, ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture, false);
    }

    @Override
    public void setCustomAnimations(PlayerOrcAnimatable animatable, long instanceId,
                                    AnimationState<PlayerOrcAnimatable> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        hideEntityOnlyBones(animatable.getPlayer());
        rotateHead(animatable.getPlayer());
    }

    private void hideEntityOnlyBones(Player player) {
        for (String name : HIDDEN_BONES) {
            GeoBone bone = this.getAnimationProcessor().getBone(name);
            if (bone != null && !bone.isHidden()) {
                bone.setHidden(true);
            }
        }

        boolean hasChestplate = PlayerRaceArmorRenderCompatibility.hasRenderableArmor(player, EquipmentSlot.CHEST);
        GeoBone belly = this.getAnimationProcessor().getBone("Belly");
        if (belly != null) {
            belly.setHidden(hasChestplate);
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
