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
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renders the fox-spirit Geo model at the player origin. */
public final class PlayerFoxSpiritRenderer extends GeoObjectRenderer<PlayerFoxSpiritAnimatable> {

    private static final float FOX_BODY_WIDTH = 0.725F;
    private static final float FOX_MODEL_HEIGHT = 1.75F;
    private static final float FOX_STANDING_RENDER_HEIGHT = 1.45F;
    private static final float SITTING_Y_OFFSET = 0.375F;
    private static final float CROUCH_Y_OFFSET = 2.0F / 16.0F;

    public PlayerFoxSpiritRenderer() {
        super(new PlayerFoxSpiritGeoModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.addRenderLayer(new PlayerFoxSpiritMouthItemLayer(this));
    }

    @Override
    public long getInstanceId(PlayerFoxSpiritAnimatable animatable) {
        return animatable.getInstanceId();
    }

    @Override
    public void preRender(PoseStack poseStack, PlayerFoxSpiritAnimatable animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          int colour) {
        Player player = animatable.getPlayer();
        float scale = player == null ? 1.0F
                : computeFitScale(getRenderWidth(player), getRenderHeight(player));

        // GeckoLib render layers call preRender again with isReRender=true.
        // The supplied PoseStack already contains the base model transform, so
        // applying our player transform twice would detach the glow layer.
        if (!isReRender) {
            this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
            if (player != null && !isInventoryLikeGui()) {
                if (player.isSleeping()) {
                    setupSleepingPose(player, poseStack, partialTick);
                } else {
                    float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
                    if (PlayerFoxSpiritAnimatable.shouldSit(player)) {
                        poseStack.translate(0.0F, SITTING_Y_OFFSET * player.getScale(), 0.0F);
                    } else if (PlayerFoxSpiritAnimatable.shouldCrouch(player)) {
                        poseStack.translate(0.0F, CROUCH_Y_OFFSET * scale, 0.0F);
                    }
                }
            }
        }

        scaleModelForRender(scale, scale, poseStack, animatable, model, isReRender,
                partialTick, packedLight, packedOverlay);
    }

    private static boolean isInventoryLikeGui() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof AbstractContainerScreen<?>;
    }

    private static float getRenderWidth(Player player) {
        return PlayerFoxSpiritAnimatable.shouldProne(player)
                ? FOX_BODY_WIDTH * player.getScale()
                : player.getBbWidth();
    }

    private static float getRenderHeight(Player player) {
        if (PlayerFoxSpiritAnimatable.shouldProne(player)) {
            return FOX_MODEL_HEIGHT * player.getScale();
        }
        return FOX_STANDING_RENDER_HEIGHT * player.getScale();
    }

    private static void setupSleepingPose(Player player, PoseStack poseStack, float partialTick) {
        Direction direction = player.getBedOrientation();
        if (direction != null) {
            float eyeOffset = player.getEyeHeight(Pose.STANDING) - 0.1F;
            poseStack.translate(
                    (float) -direction.getStepX() * eyeOffset,
                    0.0F,
                    (float) -direction.getStepZ() * eyeOffset);
            poseStack.mulPose(Axis.YP.rotationDegrees(sleepDirectionToRotation(direction)));
        } else {
            float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        }

        // The dedicated prone animation already lays the fox down.
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

    private static float computeFitScale(float width, float height) {
        return Math.min(width / FOX_BODY_WIDTH, height / FOX_MODEL_HEIGHT);
    }
}
