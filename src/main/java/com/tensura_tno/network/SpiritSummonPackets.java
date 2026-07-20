package com.tensura_tno.network;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.ability.skill.SpiritSummonSkill;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 灵之召唤技能的网络包。
 *
 * <p>S2C: OpenScreenPayload — 打开召唤选择 GUI，传递已收纳生物 ID 列表。
 * <p>C2S: SelectEntityPayload — 选择要召唤的生物 ID。
 * <p>C2S: ReleaseEntityPayload — 放生（删除）指定召唤物。
 */
public final class SpiritSummonPackets {

    // ───── S2C: Open screen with absorbed entity list ─────

    public record OpenScreenPayload(String absorbedCsv) implements CustomPacketPayload {
        public static final Type<OpenScreenPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "spirit_summon_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, OpenScreenPayload::absorbedCsv,
                        OpenScreenPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ───── C2S: Player selected an entity to summon ─────

    public record SelectEntityPayload(String entityId) implements CustomPacketPayload {
        public static final Type<SelectEntityPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "spirit_summon_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectEntityPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SelectEntityPayload::entityId,
                        SelectEntityPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ───── C2S: Player releases (deletes) an absorbed entity ─────

    public record ReleaseEntityPayload(String entityId) implements CustomPacketPayload {
        public static final Type<ReleaseEntityPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "spirit_summon_release"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseEntityPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ReleaseEntityPayload::entityId,
                        ReleaseEntityPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ───── Registration ─────

    public static void registerC2S() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                SelectEntityPayload.TYPE, SelectEntityPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> handleSelect(payload, context)));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                ReleaseEntityPayload.TYPE, ReleaseEntityPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> handleRelease(payload, context)));
    }

    public static void registerS2C() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
                OpenScreenPayload.TYPE, OpenScreenPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> openClientScreen(payload)));
    }

    // ───── Server handler ─────

    private static void handleSelect(SelectEntityPayload payload, NetworkManager.PacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        SpiritSummonSkill.summonFromPocket(player, payload.entityId());
    }

    private static void handleRelease(ReleaseEntityPayload payload, NetworkManager.PacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        String entityId = payload.entityId();
        // 从口袋中移除
        SpiritSummonSkill.SpiritSummonPockets.removeAbsorbedEntity(player, entityId);
        // 如果当前待召唤的实体是被放生的，清除待召唤状态
        String pending = player.getPersistentData().getString(SpiritSummonSkill.NBT_PENDING_SUMMON);
        if (entityId.equals(pending)) {
            player.getPersistentData().remove(SpiritSummonSkill.NBT_PENDING_SUMMON);
        }
    }

    // ───── Client screen (reflection to avoid classloading issues) ─────

    private static void openClientScreen(OpenScreenPayload payload) {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Class<?> screenClass = Class.forName("com.tensura_tno.client.screen.SpiritSummonScreen");
            Object screen = screenClass
                    .getConstructor(String.class)
                    .newInstance(payload.absorbedCsv());
            minecraftClass
                    .getMethod("setScreen", Class.forName("net.minecraft.client.gui.screens.Screen"))
                    .invoke(minecraft, screen);
        } catch (Exception ignored) {}
    }

    private SpiritSummonPackets() {}
}
