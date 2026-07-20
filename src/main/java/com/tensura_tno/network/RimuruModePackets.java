package com.tensura_tno.network;

import com.tensura_tno.TensuraTNOMod;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 利姆露模式勾选状态网络包。
 *
 * <p>玩家在转生 GUI 史莱姆种族页勾选「利姆露模式」时，客户端发送
 * {@link SetRimuruPendingPayload(boolean)} 到服务端，服务端在玩家
 * {@link net.minecraft.world.entity.player.Player#getPersistentData()}
 * 写入 {@link #NBT_PENDING_RIMURU_MODE}=true。
 *
 * <p>之后 {@link com.tensura_tno.event.RimuruModeReincarnationHandler}
 * 监听 {@code RaceEvents.SET_RACE}：若新种族是 {@code tensura:slime} 且玩家有此标记，
 * 自动学会 {@code ReincarnationMenu.RIMURU_SKILLS} 列表里的所有技能（捕食者、大贤者、
 * 各类抗性等），并把可学技能加为种族 intrinsic。
 */
public final class RimuruModePackets {

    /** PersistentData key：玩家是否在 GUI 上勾选了利姆露模式（仍未实际转生）。 */
    public static final String NBT_PENDING_RIMURU_MODE = "tensura_tno:pending_rimuru_mode";

    /**
     * 客户端临时 flag：服务端在 ResetScrollItem.resetEverything 之前发的 S2C 包会设此为 true，
     * 紧接着 ReincarnationScreen.init() 时 mixin 读取并消费该 flag（设到 screen 实例字段）。
     * volatile 保证客户端 packet 线程和 render 线程间可见性。
     */
    public static volatile boolean clientNextMenuFromCharacterReset = false;
    public static volatile boolean clientNextMenuFromCharacterResetForRules = false;

    /**
     * 客户端读：专属服配置是否允许利姆露勾选框。
     * <p>直接读 COMMON 配置（双端都会加载），不依赖网络同步避免单人开局时序竞争。
     * 注意：单人/集成服场景下调用方（mixin）会绕过此方法直接允许。
     * 专服场景下若服务端 / 客户端配置不一致，服务端 packet handler 仍会做最终防御性校验。
     */
    public static boolean isClientCheckboxEnabled() {
        return com.tensura_tno.TensuraTNOCompatConfig.isRimuruModeCheckboxEnabled();
    }

    private RimuruModePackets() {}

    // ───── S2C: server tells client that the next opened menu is a character-reset menu ─────

    public record CharacterResetMenuPayload(boolean fromCharacterReset) implements CustomPacketPayload {
        public static final Type<CharacterResetMenuPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "rimuru_char_reset_marker"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CharacterResetMenuPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, CharacterResetMenuPayload::fromCharacterReset,
                        CharacterResetMenuPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ───── C2S: client toggles the Rimuru-mode checkbox ─────

    public record SetRimuruPendingPayload(boolean checked) implements CustomPacketPayload {
        public static final Type<SetRimuruPendingPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "set_rimuru_pending"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetRimuruPendingPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, SetRimuruPendingPayload::checked,
                        SetRimuruPendingPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ───── Registration ─────

    public static void registerC2S() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                SetRimuruPendingPayload.TYPE, SetRimuruPendingPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> handleSetPending(payload, context)));
    }

    public static void registerS2C() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
                CharacterResetMenuPayload.TYPE, CharacterResetMenuPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    clientNextMenuFromCharacterReset = payload.fromCharacterReset();
                    clientNextMenuFromCharacterResetForRules = payload.fromCharacterReset();
                }));
    }

    /** 服务端：玩家用人物重置卷时，紧接着 menu 打开前发此包给客户端。 */
    public static void notifyCharacterReset(ServerPlayer player) {
        try {
            NetworkManager.sendToPlayer(player, new CharacterResetMenuPayload(true));
        } catch (Throwable t) {
            com.tensura_tno.TensuraTNOMod.LOGGER.warn(
                    "[TensuraTNO] Failed to send character-reset marker to {}: {}",
                    player.getGameProfile().getName(), t.toString());
        }
    }

    private static void handleSetPending(SetRimuruPendingPayload payload, NetworkManager.PacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        // 服务端防御：仅在专属服(dedicated)上启用 config 门控；
        // 单人 / 集成服(含 LAN host) 始终允许 — 与客户端 mixin 判据一致。
        net.minecraft.server.MinecraftServer srv = player.getServer();
        boolean dedicated = srv != null && srv.isDedicatedServer();
        if (dedicated && !com.tensura_tno.TensuraTNOCompatConfig.isRimuruModeCheckboxEnabled()) {
            player.getPersistentData().remove(NBT_PENDING_RIMURU_MODE);
            return;
        }
        if (payload.checked()) {
            player.getPersistentData().putBoolean(NBT_PENDING_RIMURU_MODE, true);
        } else {
            player.getPersistentData().remove(NBT_PENDING_RIMURU_MODE);
        }
    }
}
