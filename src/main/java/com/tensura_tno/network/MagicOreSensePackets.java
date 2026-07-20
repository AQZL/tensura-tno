package com.tensura_tno.network;

import com.tensura_tno.TensuraTNOMod;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class MagicOreSensePackets {
    public record StartScanPayload(int radius, int durationTicks) implements CustomPacketPayload {
        public static final Type<StartScanPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "magic_ore_sense_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StartScanPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, StartScanPayload::radius,
                        ByteBufCodecs.VAR_INT, StartScanPayload::durationTicks,
                        StartScanPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerS2C() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
                StartScanPayload.TYPE, StartScanPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> startClientScan(payload)));
    }

    private static void startClientScan(StartScanPayload payload) {
        try {
            Class<?> xrayClass = Class.forName("com.tensura_tno.client.MagicOreSenseXray");
            xrayClass.getMethod("startScan", int.class, int.class)
                    .invoke(null, payload.radius(), payload.durationTicks());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private MagicOreSensePackets() {
    }
}
