package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.manasmods.tensura.client.screen.ReincarnationScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/**
 * 自定义 GeoObjectRenderer：把 tensura:slime 模型渲染到玩家位置上。
 *
 * <p><b>关键：父类 {@link GeoObjectRenderer#preRender} 会做
 * {@code translate(0.5, 0.51, 0.5)} —— 这是为"在方块上摆设模型"设计的，
 * 用在玩家身上会让模型偏移半格 + 悬空半格</b>。我们完全 override {@link #preRender}：
 * <ul>
 *   <li>不做 0.5/0.51 平移（玩家是 entity，PoseStack 已在脚下原点）</li>
 *   <li>应用 yaw 旋转，让 slime 朝向跟随玩家身体朝向</li>
 *   <li>按上下文（世界 / 物品栏）缩放</li>
 * </ul>
 *
 * <p><b>缩放策略</b>：
 * <ul>
 *   <li><b>物品栏 / 创造 / 工作台等 GUI 预览</b>（{@link AbstractContainerScreen} 子类，
 *       但**不**是转生界面）：vanilla 已按 {@code 30 / 1.8 ≈ 16.67×} 放大，
 *       叠加我们额外的 {@link #INVENTORY_SCALE} 让 slime 在预览框中较小</li>
 *   <li><b>转生界面 {@link ReincarnationScreen}</b>：缩放由
 *       {@code ReincarnationScreenSlimePreviewSizeMixin} 单独控制（强制 vanilla scale=50），
 *       这里走"按碰撞箱填充"路径</li>
 *   <li><b>世界中</b>：固定使用站立时的正常大小，避免睡觉/游泳姿势改变碰撞箱后缩小</li>
 * </ul>
 */
public final class PlayerSlimeRenderer extends GeoObjectRenderer<PlayerSlimeAnimatable> {

    /** 模型未缩放尺寸（方块单位）。来自 slime.geo.json 的 cube 实测：28×20×28 像素。 */
    private static final float MODEL_WIDTH = 1.75F;
    private static final float PLAYER_STANDING_WIDTH = 0.6F;
    private static final float WORLD_SCALE = PLAYER_STANDING_WIDTH / MODEL_WIDTH;

    /** 物品栏 / 创造 / 工作台等 GUI 预览中使用的缩放。0.08 让 slime 在预览框里约占 1/5 高度。 */
    private static final float INVENTORY_SCALE = 0.08F;

    public PlayerSlimeRenderer() {
        super(new PlayerSlimeGeoModel());
    }

    @Override
    public long getInstanceId(PlayerSlimeAnimatable animatable) {
        return animatable.getInstanceId();
    }

    @Override
    public void preRender(PoseStack poseStack, PlayerSlimeAnimatable animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          int colour) {
        // 记录 PoseStack 状态供后续 layer 复用
        this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());

        Player player = animatable.getPlayer();
        Screen screen = Minecraft.getInstance().screen;
        // 任何容器 GUI（物品栏/创造/工作台/铁砧/...）都走 INVENTORY_SCALE，
        // 但转生界面例外 —— 它的缩放由 ReincarnationScreenSlimePreviewSizeMixin 控制 vanilla scale，
        // 这里走世界路径"按碰撞箱填充"配合那边的大 scale 才能正确显示
        boolean inInventoryLikeGui = (screen instanceof AbstractContainerScreen<?>)
                && !(screen instanceof ReincarnationScreen);

        float scale;
        if (inInventoryLikeGui) {
            // 物品栏 / 创造 / 工作台：vanilla 已按 30/getBbHeight 放大；
            // 不应用 yaw 旋转（GUI 已设好预览角度），改用更小固定缩放。
            scale = INVENTORY_SCALE;
        } else if (player != null) {
            // 世界 / 转生界面：应用 yaw 让 slime 朝向跟随玩家身体；固定为站立时的正常大小。
            float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
            poseStack.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
            scale = WORLD_SCALE;
        } else {
            scale = 1.0F;
        }

        scaleModelForRender(scale, scale, poseStack, animatable, model, isReRender,
                partialTick, packedLight, packedOverlay);

        // 故意不调用 super.preRender / 不做 translate(0.5, 0.51, 0.5) ——
        // 那是 GeoObjectRenderer 给方块上摆模型用的偏移，会让模型悬空 + 偏移半格。
    }

    @Override
    public void renderRecursively(PoseStack poseStack, PlayerSlimeAnimatable animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, int colour) {
        if (shouldLockDefaultShape(animatable) && "slime".equals(bone.getName())) {
            bone.updateScale(1.0F, 1.0F, 1.0F);
        }

        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    private static boolean shouldLockDefaultShape(PlayerSlimeAnimatable animatable) {
        Player player = animatable.getPlayer();
        return player != null && PlayerSlimeAnimatable.shouldKeepDefaultShape(player);
    }
}
