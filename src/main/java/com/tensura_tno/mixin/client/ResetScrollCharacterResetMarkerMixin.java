package com.tensura_tno.mixin.client;

import io.github.manasmods.tensura.item.misc.ResetScrollItem;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家用「人物重置卷」时，服务端 {@code ResetScrollItem.resetEverything} HEAD 触发，
 * 立即发 S2C packet
 * {@link com.tensura_tno.network.RimuruModePackets.CharacterResetMenuPayload} 给客户端。
 *
 * <p>vanilla 紧接着会发 menu open packet，两个 packet 在同一玩家 channel 顺序发送 →
 * 客户端必然先收到这个 marker 设置 flag，再收到 menu open 打开 ReincarnationScreen。
 * Screen 构造时由 {@link ReincarnationScreenRimuruCheckboxMixin} 消费 flag。
 *
 * <p>为什么不 mixin ReincarnationMenu 加 DataSlot：
 * 上一版试过会触发 RegisterMenuScreensEvent 时的 MixinTransformerError 崩游戏。
 * 这里改用 packet + Screen 实例字段方案，避免污染 menu 类。
 */
@Mixin(ResetScrollItem.class)
public abstract class ResetScrollCharacterResetMarkerMixin {

    @Inject(method = "resetEverything", at = @At("HEAD"), require = 0, remap = false)
    private static void tensuraTno$notifyClientCharacterReset(ServerPlayer player, CallbackInfo ci) {
        try {
            com.tensura_tno.network.RimuruModePackets.notifyCharacterReset(player);
        } catch (Throwable ignored) {
            // 通知失败不阻断主逻辑，最坏情况是客户端勾选框可见（人物重置卷的 corner case）
        }
    }
}
