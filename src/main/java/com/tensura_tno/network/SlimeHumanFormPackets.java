package com.tensura_tno.network;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.race.SlimeHumanFormState;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class SlimeHumanFormPackets {
    public static volatile boolean clientUnlocked = false;
    public static volatile boolean clientChecked = true;

    private SlimeHumanFormPackets() {
    }

    public record SetHumanFormPayload(boolean checked) implements CustomPacketPayload {
        public static final Type<SetHumanFormPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "set_slime_human_form"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetHumanFormPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, SetHumanFormPayload::checked,
                        SetHumanFormPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SyncHumanFormPayload(boolean unlocked, boolean checked) implements CustomPacketPayload {
        public static final Type<SyncHumanFormPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "sync_slime_human_form"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncHumanFormPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, SyncHumanFormPayload::unlocked,
                        ByteBufCodecs.BOOL, SyncHumanFormPayload::checked,
                        SyncHumanFormPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerC2S() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                SetHumanFormPayload.TYPE, SetHumanFormPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> handleSetHumanForm(payload, context)));
    }

    public static void registerS2C() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
                SyncHumanFormPayload.TYPE, SyncHumanFormPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    clientUnlocked = payload.unlocked();
                    clientChecked = payload.checked();
                }));
    }

    public static void syncToClient(ServerPlayer player) {
        if (player == null) return;
        try {
            NetworkManager.sendToPlayer(player, new SyncHumanFormPayload(
                    SlimeHumanFormState.isUnlocked(player),
                    SlimeHumanFormState.isChecked(player)));
        } catch (Throwable t) {
            TensuraTNOMod.LOGGER.warn(
                    "[TensuraTNO] Failed to sync slime human-form state to {}: {}",
                    player.getGameProfile().getName(), t.toString());
        }
    }

    private static void handleSetHumanForm(SetHumanFormPayload payload, NetworkManager.PacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        SlimeHumanFormState.setChecked(player, payload.checked());
    }
}
