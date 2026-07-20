package com.tensura_tno.network;

import java.util.concurrent.atomic.AtomicReference;

import com.tensura_tno.TensuraTNOMod;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

public final class ReincarnationRulesPackets {
    public static final String RULE_HARDCORE_RACE = "hardcoreRace";
    public static final String RULE_EP_DEATH_PENALTY = "epDeathPenalty";
    public static final String RULE_UNIQUE_SE_COST = "uniqueSECost";
    public static final String RULE_KEEP_INVENTORY = "keepInventory";

    public static volatile boolean clientHardcoreRace = false;
    public static volatile int clientEpDeathPenalty = 5;
    public static volatile boolean clientUniqueSECostAvailable = false;
    public static volatile boolean clientUniqueSECost = true;
    public static volatile boolean clientKeepInventory = false;

    private ReincarnationRulesPackets() {
    }

    public record RequestRulesPayload(int nonce) implements CustomPacketPayload {
        public static final Type<RequestRulesPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "request_reincarnation_rules"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestRulesPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, RequestRulesPayload::nonce,
                        RequestRulesPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetRulePayload(String ruleId, String value) implements CustomPacketPayload {
        public static final Type<SetRulePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "set_reincarnation_rule"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetRulePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SetRulePayload::ruleId,
                        ByteBufCodecs.STRING_UTF8, SetRulePayload::value,
                        SetRulePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SyncRulesPayload(
            boolean hardcoreRace,
            int epDeathPenalty,
            boolean uniqueSECostAvailable,
            boolean uniqueSECost,
            boolean keepInventory) implements CustomPacketPayload {
        public static final Type<SyncRulesPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "sync_reincarnation_rules"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncRulesPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, SyncRulesPayload::hardcoreRace,
                        ByteBufCodecs.VAR_INT, SyncRulesPayload::epDeathPenalty,
                        ByteBufCodecs.BOOL, SyncRulesPayload::uniqueSECostAvailable,
                        ByteBufCodecs.BOOL, SyncRulesPayload::uniqueSECost,
                        ByteBufCodecs.BOOL, SyncRulesPayload::keepInventory,
                        SyncRulesPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerC2S() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                RequestRulesPayload.TYPE, RequestRulesPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> syncToRequester(context)));

        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                SetRulePayload.TYPE, SetRulePayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> handleSetRule(payload, context)));
    }

    public static void registerS2C() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
                SyncRulesPayload.TYPE, SyncRulesPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    clientHardcoreRace = payload.hardcoreRace();
                    clientEpDeathPenalty = payload.epDeathPenalty();
                    clientUniqueSECostAvailable = payload.uniqueSECostAvailable();
                    clientUniqueSECost = payload.uniqueSECost();
                    clientKeepInventory = payload.keepInventory();
                }));
    }

    public static void requestSync() {
        try {
            NetworkManager.sendToServer(new RequestRulesPayload(0));
        } catch (Throwable t) {
            TensuraTNOMod.LOGGER.warn("[TensuraTNO] Failed to request reincarnation gamerules: {}", t.toString());
        }
    }

    private static void syncToRequester(NetworkManager.PacketContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            syncToClient(player);
        }
    }

    private static void handleSetRule(SetRulePayload payload, NetworkManager.PacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null || !canEditRules(player, server)) {
            syncToClient(player);
            return;
        }

        GameRules rules = player.serverLevel().getGameRules();
        try {
            switch (payload.ruleId()) {
                case RULE_HARDCORE_RACE -> rules.getRule(TensuraGameRules.HARDCORE_RACE)
                        .set(Boolean.parseBoolean(payload.value()), server);
                case RULE_EP_DEATH_PENALTY -> rules.getRule(TensuraGameRules.EP_DEATH_PENALTY)
                        .set(Math.max(0, Integer.parseInt(payload.value())), server);
                case RULE_KEEP_INVENTORY -> rules.getRule(GameRules.RULE_KEEPINVENTORY)
                        .set(Boolean.parseBoolean(payload.value()), server);
                case RULE_UNIQUE_SE_COST -> {
                    GameRules.Key<GameRules.BooleanValue> key = findBooleanRuleKey(RULE_UNIQUE_SE_COST);
                    if (key != null) {
                        rules.getRule(key).set(Boolean.parseBoolean(payload.value()), server);
                    }
                }
                default -> {
                    return;
                }
            }
        } catch (RuntimeException e) {
            TensuraTNOMod.LOGGER.warn("[TensuraTNO] Failed to update gamerule {}={}: {}",
                    payload.ruleId(), payload.value(), e.toString());
        }
        syncToClient(player);
    }

    private static void syncToClient(ServerPlayer player) {
        if (player == null) {
            return;
        }
        GameRules rules = player.serverLevel().getGameRules();
        GameRules.Key<GameRules.BooleanValue> uniqueKey = findBooleanRuleKey(RULE_UNIQUE_SE_COST);
        boolean uniqueAvailable = uniqueKey != null;
        boolean uniqueValue = uniqueAvailable && rules.getBoolean(uniqueKey);
        try {
            NetworkManager.sendToPlayer(player, new SyncRulesPayload(
                    rules.getBoolean(TensuraGameRules.HARDCORE_RACE),
                    rules.getInt(TensuraGameRules.EP_DEATH_PENALTY),
                    uniqueAvailable,
                    uniqueValue,
                    rules.getBoolean(GameRules.RULE_KEEPINVENTORY)));
        } catch (Throwable t) {
            TensuraTNOMod.LOGGER.warn("[TensuraTNO] Failed to sync reincarnation gamerules to {}: {}",
                    player.getGameProfile().getName(), t.toString());
        }
    }

    private static GameRules.Key<GameRules.BooleanValue> findBooleanRuleKey(String id) {
        AtomicReference<GameRules.Key<GameRules.BooleanValue>> result = new AtomicReference<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key,
                                     GameRules.Type<GameRules.BooleanValue> type) {
                if (key.getId().equals(id)) {
                    result.set(key);
                }
            }
        });
        return result.get();
    }

    private static boolean canEditRules(ServerPlayer player, MinecraftServer server) {
        if (server.isDedicatedServer()) {
            return player.hasPermissions(2);
        }
        return server.isSingleplayerOwner(player.getGameProfile());
    }
}
