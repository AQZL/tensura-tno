package com.tensura_tno.compat;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.storage.api.Storage;
import io.github.manasmods.manascore.storage.api.StorageHolder;
import io.github.manasmods.manascore.storage.api.StorageKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * 与 {@code tensura_better_subs}（bett）的弱耦合集成：不将其列入 Gradle 依赖，运行时反射调用。
 */
public final class BetterSubsFoxCompat {

    private BetterSubsFoxCompat() {}

    public static boolean tryOpenSubordinateMainMenu(ServerPlayer player, int targetEntityId) {
        if (!ModList.get().isLoaded("tensura_better_subs")) {
            return false;
        }
        try {
            Class<?> payloadClass = Class.forName(
                    "com.trbeyond.tensura_better_subs.network.s2c.OpenHumanoidSkillMenuPayload");
            Object payload = payloadClass.getConstructor(int.class).newInstance(targetEntityId);
            NetworkManager.sendToPlayer(player, (CustomPacketPayload) payload);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void saveExpandedSkillStorage(LivingEntity fox, CompoundTag statsRoot) {
        if (!ModList.get().isLoaded("tensura_better_subs")) {
            return;
        }
        try {
            Class<?> essClass = Class.forName(
                    "com.trbeyond.tensura_better_subs.entity.ExpandedSkillStorage");
            StorageKey<? extends Storage> key =
                    (StorageKey<? extends Storage>) essClass.getMethod("getKey").invoke(null);
            Storage storage = ((StorageHolder) fox).manasCore$getStorage(key);
            if (storage != null) {
                CompoundTag bett = new CompoundTag();
                storage.save(bett);
                statsRoot.put("TnoBettExpanded", bett);
            }
        } catch (Throwable ignored) {
            // 存储尚未初始化等情况忽略
        }
    }

    public static void loadExpandedSkillStorage(LivingEntity fox, CompoundTag statsRoot) {
        if (!statsRoot.contains("TnoBettExpanded", Tag.TAG_COMPOUND)) {
            return;
        }
        if (!ModList.get().isLoaded("tensura_better_subs")) {
            return;
        }
        try {
            Class<?> essClass = Class.forName(
                    "com.trbeyond.tensura_better_subs.entity.ExpandedSkillStorage");
            StorageKey<? extends Storage> key =
                    (StorageKey<? extends Storage>) essClass.getMethod("getKey").invoke(null);
            Storage storage = ((StorageHolder) fox).manasCore$getStorage(key);
            if (storage != null) {
                storage.load(statsRoot.getCompound("TnoBettExpanded"));
            }
        } catch (Throwable ignored) {
        }
    }
}
