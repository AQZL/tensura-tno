package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.manasmods.tensura.item.tool.SimpleShieldItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Renders the real player's held items on the lizardman hand bones.
 */
final class PlayerLizardmanItemLayer extends BlockAndItemGeoLayer<PlayerLizardmanAnimatable> {

    private static final String LEFT_HAND = "leftItem";
    private static final String RIGHT_HAND = "rightItem";

    PlayerLizardmanItemLayer(GeoRenderer<PlayerLizardmanAnimatable> renderer) {
        super(renderer);
    }

    @Override
    protected @Nullable ItemStack getStackForBone(GeoBone bone, PlayerLizardmanAnimatable animatable) {
        Player player = animatable.getPlayer();
        if (player == null) return null;

        ItemStack stack = switch (bone.getName()) {
            case LEFT_HAND -> isLeftHanded(player) ? player.getMainHandItem() : player.getOffhandItem();
            case RIGHT_HAND -> isLeftHanded(player) ? player.getOffhandItem() : player.getMainHandItem();
            default -> ItemStack.EMPTY;
        };

        return stack.isEmpty() ? null : stack;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack,
                                                          PlayerLizardmanAnimatable animatable) {
        return switch (bone.getName()) {
            case LEFT_HAND, RIGHT_HAND -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            default -> ItemDisplayContext.NONE;
        };
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                      PlayerLizardmanAnimatable animatable, MultiBufferSource bufferSource,
                                      float partialTick, int packedLight, int packedOverlay) {
        Player player = animatable.getPlayer();
        if (player == null) {
            super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource,
                    partialTick, packedLight, packedOverlay);
            return;
        }

        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        if (isShield(stack)) {
            if (RIGHT_HAND.equals(bone.getName())) {
                poseStack.translate(0.0F, 0.125F, -0.25F);
            } else if (LEFT_HAND.equals(bone.getName())) {
                poseStack.translate(0.0F, 0.125F, 0.25F);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(player, stack,
                getTransformTypeForStack(bone, stack, animatable), false, poseStack,
                bufferSource, player.level(), packedLight, packedOverlay, player.getId());
    }

    private static boolean isLeftHanded(Player player) {
        return player.getMainArm() == HumanoidArm.LEFT;
    }

    private static boolean isShield(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem || stack.getItem() instanceof SimpleShieldItem;
    }
}
