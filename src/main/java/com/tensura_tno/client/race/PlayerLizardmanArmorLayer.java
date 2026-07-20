package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/**
 * Player-specific version of GeckoLib's armor layer. The animatable is only a
 * render proxy, so armor stacks must be read from the real player.
 */
final class PlayerLizardmanArmorLayer extends GeoRenderLayer<PlayerLizardmanAnimatable> {

    private static final HumanoidModel<LivingEntity> INNER_ARMOR_MODEL =
            new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
    private static final HumanoidModel<LivingEntity> OUTER_ARMOR_MODEL =
            new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));

    private static final String LEFT_BOOT = "leftBootArmor";
    private static final String RIGHT_BOOT = "rightBootArmor";
    private static final String LEFT_LEG = "leftLegArmor";
    private static final String RIGHT_LEG = "rightLegArmor";
    private static final String BODY = "bodyArmor";
    private static final String RIGHT_ARM = "rightArmArmor";
    private static final String LEFT_ARM = "leftArmArmor";
    private static final String HEAD = "headArmor";

    @Nullable private ItemStack helmetStack;
    @Nullable private ItemStack chestplateStack;
    @Nullable private ItemStack leggingsStack;
    @Nullable private ItemStack bootsStack;

    PlayerLizardmanArmorLayer(GeoRenderer<PlayerLizardmanAnimatable> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack poseStack, PlayerLizardmanAnimatable animatable,
                          software.bernie.geckolib.cache.object.BakedGeoModel bakedModel,
                          @Nullable RenderType renderType, MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, float partialTick,
                          int packedLight, int packedOverlay) {
        Player player = animatable.getPlayer();
        this.helmetStack = getEquipped(player, EquipmentSlot.HEAD);
        this.chestplateStack = getEquipped(player, EquipmentSlot.CHEST);
        this.leggingsStack = getEquipped(player, EquipmentSlot.LEGS);
        this.bootsStack = getEquipped(player, EquipmentSlot.FEET);
    }

    @Override
    public void renderForBone(PoseStack poseStack, PlayerLizardmanAnimatable animatable,
                              GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay) {
        ItemStack armorStack = getArmorItemForBone(bone);
        if (armorStack == null || !(armorStack.getItem() instanceof ArmorItem)) return;
        if (bone.getCubes().isEmpty()) return;

        EquipmentSlot slot = getEquipmentSlotForBone(bone);
        HumanoidModel<?> model = slot == EquipmentSlot.LEGS ? INNER_ARMOR_MODEL : OUTER_ARMOR_MODEL;
        ModelPart modelPart = getModelPartForBone(bone, model);

        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        prepModelPartForRender(poseStack, bone, modelPart);
        modelPart.visible = true;
        renderVanillaArmorPiece(poseStack, bone, slot, armorStack, modelPart,
                bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Nullable
    private ItemStack getArmorItemForBone(GeoBone bone) {
        ItemStack stack = switch (bone.getName()) {
            case LEFT_BOOT, RIGHT_BOOT -> this.bootsStack;
            case LEFT_LEG, RIGHT_LEG -> this.leggingsStack;
            case BODY, RIGHT_ARM, LEFT_ARM -> this.chestplateStack;
            case HEAD -> this.helmetStack;
            default -> null;
        };

        return stack == null || stack.isEmpty() ? null : stack;
    }

    private static EquipmentSlot getEquipmentSlotForBone(GeoBone bone) {
        return switch (bone.getName()) {
            case LEFT_BOOT, RIGHT_BOOT -> EquipmentSlot.FEET;
            case LEFT_LEG, RIGHT_LEG -> EquipmentSlot.LEGS;
            case HEAD -> EquipmentSlot.HEAD;
            default -> EquipmentSlot.CHEST;
        };
    }

    private static ModelPart getModelPartForBone(GeoBone bone, HumanoidModel<?> baseModel) {
        return switch (bone.getName()) {
            case LEFT_BOOT, LEFT_LEG -> baseModel.leftLeg;
            case RIGHT_BOOT, RIGHT_LEG -> baseModel.rightLeg;
            case RIGHT_ARM -> baseModel.rightArm;
            case LEFT_ARM -> baseModel.leftArm;
            case HEAD -> baseModel.head;
            default -> baseModel.body;
        };
    }

    private static void renderVanillaArmorPiece(PoseStack poseStack, GeoBone bone, EquipmentSlot slot,
                                                ItemStack armorStack, ModelPart modelPart,
                                                MultiBufferSource bufferSource, int packedLight,
                                                int packedOverlay) {
        Holder<ArmorMaterial> material = ((ArmorItem) armorStack.getItem()).getMaterial();

        for (ArmorMaterial.Layer layer : material.value().layers()) {
            int color = armorStack.is(ItemTags.DYEABLE)
                    ? DyedItemColor.getOrDefault(armorStack, -6265536)
                    : -1;
            VertexConsumer armorBuffer = getVanillaArmorBuffer(bufferSource, armorStack,
                    slot, bone, layer, false);
            modelPart.render(poseStack, armorBuffer, packedLight, packedOverlay, color);
        }

        ArmorTrim trim = armorStack.get(DataComponents.TRIM);
        if (trim != null) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                    .getAtlas(Sheets.ARMOR_TRIMS_SHEET)
                    .getSprite(slot == EquipmentSlot.LEGS
                            ? trim.innerTexture(material)
                            : trim.outerTexture(material));
            VertexConsumer trimBuffer = sprite.wrap(bufferSource.getBuffer(
                    Sheets.armorTrimsSheet(trim.pattern().value().decal())));
            modelPart.render(poseStack, trimBuffer, packedLight, packedOverlay);
        }

        if (armorStack.hasFoil()) {
            modelPart.render(poseStack, getVanillaArmorBuffer(bufferSource, armorStack,
                    slot, bone, null, true), packedLight, packedOverlay, Color.WHITE.argbInt());
        }
    }

    private static VertexConsumer getVanillaArmorBuffer(MultiBufferSource bufferSource,
                                                        ItemStack stack, EquipmentSlot slot,
                                                        GeoBone bone,
                                                        @Nullable ArmorMaterial.Layer layer,
                                                        boolean forGlint) {
        if (forGlint) return bufferSource.getBuffer(RenderType.armorEntityGlint());
        return bufferSource.getBuffer(RenderType.armorCutoutNoCull(layer.texture(slot == EquipmentSlot.LEGS)));
    }

    private static void prepModelPartForRender(PoseStack poseStack, GeoBone bone, ModelPart sourcePart) {
        GeoCube firstCube = bone.getCubes().getFirst();
        ReferenceSize armorCube = getReferenceSizeForBone(bone);

        double armorBoneSizeX = firstCube.size().x();
        double armorBoneSizeY = firstCube.size().y();
        double armorBoneSizeZ = firstCube.size().z();
        double actualArmorSizeX = armorCube.x();
        double actualArmorSizeY = armorCube.y();
        double actualArmorSizeZ = armorCube.z();

        float scaleX = (float)(armorBoneSizeX / actualArmorSizeX);
        float scaleY = (float)(armorBoneSizeY / actualArmorSizeY);
        float scaleZ = (float)(armorBoneSizeZ / actualArmorSizeZ);

        sourcePart.setPos(
                -(bone.getPivotX() - ((bone.getPivotX() * scaleX) - bone.getPivotX()) / scaleX),
                -(bone.getPivotY() - ((bone.getPivotY() * scaleY) - bone.getPivotY()) / scaleY),
                bone.getPivotZ() - ((bone.getPivotZ() * scaleZ) - bone.getPivotZ()) / scaleZ);
        sourcePart.xRot = -bone.getRotX();
        sourcePart.yRot = -bone.getRotY();
        sourcePart.zRot = bone.getRotZ();

        poseStack.scale(scaleX, scaleY, scaleZ);
    }

    private static ReferenceSize getReferenceSizeForBone(GeoBone bone) {
        return switch (bone.getName()) {
            case HEAD -> new ReferenceSize(8.0D, 8.0D, 8.0D);
            case BODY -> new ReferenceSize(8.0D, 12.0D, 4.0D);
            default -> new ReferenceSize(4.0D, 12.0D, 4.0D);
        };
    }

    private static ItemStack getEquipped(@Nullable Player player, EquipmentSlot slot) {
        return PlayerRaceArmorRenderCompatibility.getRenderableArmor(player, slot);
    }

    private record ReferenceSize(double x, double y, double z) {}
}
