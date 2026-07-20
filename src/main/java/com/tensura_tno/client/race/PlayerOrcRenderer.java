package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/**
 * Renders the regular Tensura orc Geo model at a player origin.
 */
public final class PlayerOrcRenderer extends GeoObjectRenderer<PlayerOrcAnimatable> {

    private static final float ORC_ENTITY_WIDTH = 0.8F;
    private static final float ORC_ENTITY_HEIGHT = 2.5F;
    private static final float PLAYER_STANDING_HEIGHT = 1.8F;
    private static final float SITTING_Y_OFFSET = 0.55F;

    public PlayerOrcRenderer() {
        super(new PlayerOrcGeoModel());
        this.addRenderLayer(new PlayerOrcArmorLayer(this));
        this.addRenderLayer(new PlayerOrcItemLayer(this));
    }

    @Override
    public long getInstanceId(PlayerOrcAnimatable animatable) {
        return animatable.getInstanceId();
    }

    @Override
    public void preRender(PoseStack poseStack, PlayerOrcAnimatable animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          int colour) {
        this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());

        Player player = animatable.getPlayer();
        animatable.syncPlayerState(partialTick);
        if (player != null && !isInventoryLikeGui()) {
            if (player.isSleeping()) {
                setupSleepingPose(player, poseStack, partialTick);
            } else {
                float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
                if (PlayerOrcAnimatable.shouldSit(player)) {
                    poseStack.translate(0.0F, SITTING_Y_OFFSET * player.getScale(), 0.0F);
                }
            }
        }

        float scale = player == null
                ? 1.0F
                : computeFitScale(getRenderWidth(player), getRenderHeight(player));
        scaleModelForRender(scale, scale, poseStack, animatable, model, isReRender,
                partialTick, packedLight, packedOverlay);
    }

    private static boolean isInventoryLikeGui() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof AbstractContainerScreen<?>;
    }

    private static boolean usesStandingScale(Player player) {
        return player.isShiftKeyDown() || player.isVisuallySwimming() || player.isSleeping();
    }

    private static float getRenderWidth(Player player) {
        return player.isSleeping()
                ? ORC_ENTITY_WIDTH * player.getScale()
                : player.getBbWidth();
    }

    private static float getRenderHeight(Player player) {
        return usesStandingScale(player)
                ? PLAYER_STANDING_HEIGHT * player.getScale()
                : player.getBbHeight();
    }

    private static void setupSleepingPose(Player player, PoseStack poseStack, float partialTick) {
        Direction direction = player.getBedOrientation();
        if (direction != null) {
            float eyeOffset = player.getEyeHeight(Pose.STANDING) - 0.1F;
            poseStack.translate(
                    (float)(-direction.getStepX()) * eyeOffset,
                    0.0F,
                    (float)(-direction.getStepZ()) * eyeOffset);
            poseStack.mulPose(Axis.YP.rotationDegrees(sleepDirectionToRotation(direction)));
        } else {
            float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        }

        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
    }

    private static float sleepDirectionToRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90.0F;
            case WEST -> 0.0F;
            case NORTH -> 270.0F;
            case EAST -> 180.0F;
            default -> 0.0F;
        };
    }

    private static float computeFitScale(float bbWidth, float bbHeight) {
        float byWidth = bbWidth / ORC_ENTITY_WIDTH;
        float byHeight = bbHeight / ORC_ENTITY_HEIGHT;
        return Math.min(byWidth, byHeight);
    }
}
