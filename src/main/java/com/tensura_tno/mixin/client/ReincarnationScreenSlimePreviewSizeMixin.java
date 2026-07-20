package com.tensura_tno.mixin.client;

import java.util.List;

import com.tensura_tno.race.SlimeRaceHelper;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.tensura.client.screen.ReincarnationScreen;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 转生界面（{@link ReincarnationScreen}）选中史莱姆家族或蜘蛛种族时，强制把玩家预览的渲染 scale
 * 拉大，避免因模组配置的体型缩小让预览模型显得很小。
 *
 * <p>拦截 {@code renderBg} 中 {@link InventoryScreen#renderEntityInInventoryFollowsMouse}
 * 调用，按当前选中种族重写 scale 参数：
 * <ul>
 *   <li>选中 slime 家族（slime / metal_slime / demon_slime / god_slime）→ 用固定 {@link #SLIME_PREVIEW_SCALE}</li>
 *   <li>选中蜘蛛种族（small_lesser_taratect）→ 用固定 {@link #TARATECT_PREVIEW_SCALE}</li>
 *   <li>其它种族 / random → 沿用原版 scale（不动）</li>
 * </ul>
 */
@Mixin(value = ReincarnationScreen.class, priority = 900)
public abstract class ReincarnationScreenSlimePreviewSizeMixin {

    /** 选中 slime 家族时使用的预览缩放（比其它种族 30 大一些以让 slime 看起来明显）。 */
    private static final int SLIME_PREVIEW_SCALE = 50;

    /** 让 slime 在预览框中往上移的 Y 偏移（像素）。正值向上。 */
    private static final int SLIME_PREVIEW_Y_OFFSET = 20;

    /** 选中蜘蛛种族时使用的预览缩放。 */
    private static final int TARATECT_PREVIEW_SCALE = 35;

    /** 让蜘蛛在预览框中往上移的 Y 偏移（像素）。 */
    private static final int TARATECT_PREVIEW_Y_OFFSET = 10;

    private static final ResourceLocation SMALL_LESSER_TARATECT_ID = ResourceLocation.fromNamespaceAndPath(
            "tensura_kumodesu", "small_lesser_taratect");

    @Redirect(
        method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"
        ),
        require = 0
    )
    private void tensuraTno$resizeSlimePreview(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                                int scale, float yOffset, float mouseX, float mouseY,
                                                LivingEntity entity) {
        if (isSlimeRaceSelected()) {
            // 缩放变大 + 把矩形整体往上挪（y1/y2 都减），slime 显得更大且位置上移
            int adjustedY1 = y1 - SLIME_PREVIEW_Y_OFFSET;
            int adjustedY2 = y2 - SLIME_PREVIEW_Y_OFFSET;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, x1, adjustedY1, x2, adjustedY2,
                    SLIME_PREVIEW_SCALE, yOffset, mouseX, mouseY, entity);
        } else if (isTaratectRaceSelected()) {
            int adjustedY1 = y1 - TARATECT_PREVIEW_Y_OFFSET;
            int adjustedY2 = y2 - TARATECT_PREVIEW_Y_OFFSET;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, x1, adjustedY1, x2, adjustedY2,
                    TARATECT_PREVIEW_SCALE, yOffset, mouseX, mouseY, entity);
        } else {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, x1, y1, x2, y2, scale, yOffset, mouseX, mouseY, entity);
        }
    }

    /** 当前选中的种族是否属于 slime 家族（不包括 random 格）。 */
    private boolean isSlimeRaceSelected() {
        try {
            ResourceLocation id = getSelectedRaceId();
            return id != null && SlimeRaceHelper.SLIME_RACE_IDS.contains(id);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 当前选中的种族是否是蜘蛛种族。 */
    private boolean isTaratectRaceSelected() {
        try {
            ResourceLocation id = getSelectedRaceId();
            return SMALL_LESSER_TARATECT_ID.equals(id);
        } catch (Throwable t) {
            return false;
        }
    }

    private ResourceLocation getSelectedRaceId() {
        ReincarnationScreen self = (ReincarnationScreen) (Object) this;
        ReincarnationMenu menu = self.getMenu();
        if (menu == null) return null;

        int index = menu.selectedManasRaceIndex.get();
        List<ManasRace> pool = menu.getRacePool();
        if (pool == null || index < 0 || index >= pool.size()) return null;

        ManasRace race = pool.get(index);
        if (race == null) return null;
        return race.getRegistryName();
    }
}
