package com.tensura_tno.mixin.client;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 CaptivatorSkill 在 Sinytra Connector 下崩溃的问题。
 *
 * 根本原因：
 *   SyncLieModePacket.TYPE 在 Connector 环境中未注册 codec，
 *   Architectury 的 NetworkAggregator.collectPackets 中 codec 字段为 null，
 *   调用 NetworkManager.sendToPlayer 时触发 NullPointerException。
 *
 * 修复方式：Redirect 拦截该调用，用 try-catch 忽略 NPE（Connector 兼容性问题）。
 */
@Mixin(targets = "io.github.Memoires.mysticism.ability.skill.unique.CaptivatorSkill", remap = false)
public class CaptivatorSkillNetworkFixMixin {

    @Redirect(
        method = "switchSkillMode",
        at = @At(
            value = "INVOKE",
            target = "Ldev/architectury/networking/NetworkManager;sendToPlayer(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            remap = false
        ),
        remap = false
    )
    private void tensuraTno$safeSendLieModePacket(ServerPlayer player, CustomPacketPayload packet) {
        try {
            NetworkManager.sendToPlayer(player, packet);
        } catch (Exception ignored) {
            // SyncLieModePacket codec 未注册（Sinytra Connector 兼容性问题），忽略异常
        }
    }
}
