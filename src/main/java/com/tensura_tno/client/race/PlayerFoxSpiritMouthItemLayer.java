package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/** Renders only the player's main-hand item as an item held in the fox's mouth. */
final class PlayerFoxSpiritMouthItemLayer extends BlockAndItemGeoLayer<PlayerFoxSpiritAnimatable> {

    private static final String MOUTH_BONE = "muzzle";

    PlayerFoxSpiritMouthItemLayer(GeoRenderer<PlayerFoxSpiritAnimatable> renderer) {
        super(renderer);
    }

    @Override
    protected @Nullable ItemStack getStackForBone(GeoBone bone, PlayerFoxSpiritAnimatable animatable) {
        if (!MOUTH_BONE.equals(bone.getName())) return null;
        Player player = animatable.getPlayer();
        if (player == null || player.getMainHandItem().isEmpty()) return null;
        return player.getMainHandItem();
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack,
                                                          PlayerFoxSpiritAnimatable animatable) {
        return MOUTH_BONE.equals(bone.getName())
                ? ItemDisplayContext.GROUND
                : ItemDisplayContext.NONE;
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                      PlayerFoxSpiritAnimatable animatable, MultiBufferSource bufferSource,
                                      float partialTick, int packedLight, int packedOverlay) {
        Player player = animatable.getPlayer();
        if (player == null) return;

        // Adapted from vanilla FoxHeldItemLayer. The muzzle bone is already
        // positioned at the snout, so only a small forward/down adjustment is needed.
        float horizontalOffset = isWeapon(stack) ? -0.15625F : 0.0F;
        poseStack.translate(horizontalOffset, -0.025F, -0.32F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                player,
                stack,
                ItemDisplayContext.GROUND,
                false,
                poseStack,
                bufferSource,
                player.level(),
                packedLight,
                packedOverlay,
                player.getId());
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof ProjectileWeaponItem;
    }
}
