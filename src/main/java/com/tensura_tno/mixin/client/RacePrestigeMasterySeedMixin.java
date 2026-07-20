package com.tensura_tno.mixin.client;

import com.tensura_tno.ftb.MasterySeedHelper;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 STExtras 种族威望任务在激活时不回溯检测已精通技能的 Bug。
 *
 * <p><b>Bug 根因</b>：种族威望 MASTER 类型任务依赖 MASTERY_POINT 事件来检测
 * 技能精通，但该事件仅在 addMasteryPoint 被调用时触发。如果技能在
 * 威望任务激活前已精通，addMasteryPoint 不再被调用（canTick 返回 false），
 * 事件不再触发，任务永远无法完成。</p>
 *
 * <p><b>修复方式</b>：在 ensureForCurrentRace 返回后，检查所有活跃的
 * MASTER/MASTER_TYPE/MASTER_TYPE_MAGIC/MASTER_SUBTYPE_MAGIC 任务，
 * 如果玩家已精通对应技能则立即标记完成或设置初始进度。</p>
 *
 * <p>注入目标选择 ensureForCurrentRace 而非 applyRaceQuests，
 * 因为后者的参数包含 STExtrasQuestStorage 等编译期不可用的类型，
 * Mixin 的 {@code @Inject} 要求处理器签名必须完整匹配目标方法参数。</p>
 */
@Mixin(targets = "org.crypticdev.stextras.quest.assignment.RacePrestigeAssignmentService", remap = false)
public class RacePrestigeMasterySeedMixin {

    @Inject(
        method = "ensureForCurrentRace(Lnet/minecraft/server/level/ServerPlayer;ZZ)V",
        at = @At("RETURN"),
        remap = false
    )
    private static void tno$seedMasteryOnActivation(
            ServerPlayer player, boolean statFirst, boolean sync,
            CallbackInfo ci
    ) {
        MasterySeedHelper.seedMasteryQuests(player);
    }
}
