package com.tensura_tno.mixin.client;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.data.TensuraItemTags;
import io.github.manasmods.tensura.item.misc.ResetScrollItem;
import io.github.manasmods.tensura.registry.advancement.TensuraCriteriaTriggers;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.player.ITensuraPlayer;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复：stextras 锁定学者（Scholar）技能后，使用人物重置卷轴会导致
 * 蓝图图纸解锁失效的问题。
 * <p>
 * 原因：{@code resetEverything} 无条件调用 {@code playerData.clearSchematics()}，
 * 但 stextras 的 HandleSkillLock 阻止了学者技能被移除。技能仍在，蓝图却被清空，
 * 且 {@code onLearnSkill} 不会再次触发。
 * <p>
 * 修复方式：在 resetEverything 末尾检查玩家是否仍拥有学者技能，若有则重新解锁全部蓝图。
 */
@Mixin(ResetScrollItem.class)
public class ResetScrollScholarFixMixin {

    @Unique
    private static final ResourceLocation tensuraTno$SCHOLAR_ID =
        ResourceLocation.fromNamespaceAndPath("mysticism", "scholar");

    @Inject(method = "resetEverything", at = @At("TAIL"))
    private static void tensuraTno$restoreLockedScholarSchematics(ServerPlayer player, CallbackInfo ci) {
        ManasSkill scholarSkill = (ManasSkill) SkillAPI.getSkillRegistry().get(tensuraTno$SCHOLAR_ID);
        if (scholarSkill == null) return;
        if (!SkillUtils.hasSkill(player, scholarSkill)) return;

        ITensuraPlayer cap = TensuraStorages.getPlayerDataFrom(player);
        boolean unlockedAny = false;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDefaultInstance().is(TensuraItemTags.SCHEMATICS) && !cap.hasSchematic(item)) {
                cap.unlockSchematic(item.getDefaultInstance());
                unlockedAny = true;
            }
        }
        if (unlockedAny) {
            cap.markDirty();
            ((PlayerTrigger) TensuraCriteriaTriggers.LEARN_ALL_SCHEMATICS.get()).trigger(player);
        }
    }
}
