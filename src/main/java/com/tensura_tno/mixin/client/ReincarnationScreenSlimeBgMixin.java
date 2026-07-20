package com.tensura_tno.mixin.client;

import java.util.List;
import java.util.Set;

import com.tensura_tno.client.race.PlayerLizardmanRenderManager;
import com.tensura_tno.client.race.PlayerOrcRenderManager;
import com.tensura_tno.race.SlimeRaceHelper;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.tensura.client.screen.ReincarnationScreen;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 当玩家在 tensura 转生界面（{@link ReincarnationScreen}）选中史莱姆家族
 * （{@code tensura:slime / metal_slime / demon_slime / god_slime}）或蜘蛛种族
 * （{@code tensura_kumodesu:small_lesser_taratect}）时，
 * 把背景纹理替换为模组对应的专属贴图。
 *
 * <p>实现：用 {@code @Redirect} 拦截 {@code renderBg} 与 {@code renderButtons} 中
 * 所有 {@code GuiGraphics.blit(ResourceLocation, x, y, u, v, w, h)} 调用，
 * 把传入的 BACKGROUND 替换为专属贴图。其它种族（含 random 格）保留原贴图。
 *
 * <p>布局/UV 完全不变 —— 替换贴图必须与原版 gui.png 同尺寸、同 UV 布局。
 */
@Mixin(value = ReincarnationScreen.class, priority = 900)
public abstract class ReincarnationScreenSlimeBgMixin {

    private static final ResourceLocation SLIME_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/reincarnation/slime_gui.png");

    private static final ResourceLocation TARATECT_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/reincarnation/taratect_gui.png");

    private static final ResourceLocation LIZARDMAN_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/reincarnation/lizardman_gui.png");

    private static final ResourceLocation ORC_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/reincarnation/orc_gui.png");

    private static final ResourceLocation EX_MACHINA_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "tensura_tno", "textures/gui/reincarnation/ex_machina_gui.png");

    private static final ResourceLocation SMALL_LESSER_TARATECT_ID = ResourceLocation.fromNamespaceAndPath(
            "tensura_kumodesu", "small_lesser_taratect");

    private static final Set<ResourceLocation> EX_MACHINA_RACE_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "machine"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "befehler"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "kampf"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "zeichen"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "pruefer"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "seher"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "hubie_dora"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "einzeig"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "emir_eins"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "horou"),
            ResourceLocation.fromNamespaceAndPath("tennogamenolife", "puraiya"));

    @Redirect(
        method = {
            "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            "renderButtons"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"
        ),
        require = 0
    )
    private void tensuraTno$swapBackgroundForSlime(GuiGraphics graphics, ResourceLocation texture,
                                                    int x, int y, int u, int v, int w, int h) {
        graphics.blit(selectBackground(texture), x, y, u, v, w, h);
    }

    /** 选中 slime 家族 → slime 专属背景；其它种族 / random / 异常 → 原始背景。 */
    private ResourceLocation selectBackground(ResourceLocation fallback) {
        try {
            ReincarnationScreen self = (ReincarnationScreen) (Object) this;
            ReincarnationMenu menu = self.getMenu();
            if (menu == null) return fallback;

            int index = menu.selectedManasRaceIndex.get();
            List<ManasRace> pool = menu.getRacePool();
            if (pool == null || index < 0 || index >= pool.size()) {
                // 越界 = "random"格，沿用原版背景
                return fallback;
            }

            ManasRace race = pool.get(index);
            if (race == null) return fallback;
            ResourceLocation id = race.getRegistryName();
            if (id == null) return fallback;

            if (SlimeRaceHelper.SLIME_RACE_IDS.contains(id)) {
                return SLIME_BACKGROUND;
            }
            if (PlayerLizardmanRenderManager.LIZARDMAN_RACE_IDS.contains(id)) {
                return LIZARDMAN_BACKGROUND;
            }
            if (PlayerOrcRenderManager.ORC_RACE_IDS.contains(id)) {
                return ORC_BACKGROUND;
            }
            if (SMALL_LESSER_TARATECT_ID.equals(id)) {
                return TARATECT_BACKGROUND;
            }
            if (EX_MACHINA_RACE_IDS.contains(id)) {
                return EX_MACHINA_BACKGROUND;
            }
            return fallback;
        } catch (Throwable t) {
            // 任何异常都安全降级到原始背景
            return fallback;
        }
    }
}
