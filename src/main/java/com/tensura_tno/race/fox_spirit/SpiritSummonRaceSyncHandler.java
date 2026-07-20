package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.ability.skill.SpiritSummonSkill;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.race.api.RaceEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * 转生回同种族（或换到任意狐灵种族）时，重新同步 race instance 上的
 * {@code absorbedTypesCount} 标签，使其反映玩家口袋 NBT 里现存的收纳种类数。
 * <p>
 * <b>修复 Bug：</b>玩家反馈"用转生回同种族刷新种族任务"时，新建的 race instance
 * 计数器从 0 开始，但口袋（{@code player.getPersistentData}）里已存 40+ 种实体，
 * 重复收纳会被去重逻辑直接拒绝 → 任务无法推进。
 * <p>
 * 此处理器在 {@link RaceEvents#SET_RACE} 触发时，若新种族属于狐灵家族，
 * 把口袋长度回填到 {@code newInstance.getOrCreateTag().putInt("absorbedTypesCount", ...)}。
 * 由于 {@code RaceStorage.setRace} 在调用此事件后才把 {@code newInstance} 写入存储并标记 dirty，
 * 这里直接写 tag 即可被随后的 markDirty 一同持久化。
 */
public final class SpiritSummonRaceSyncHandler {

    /** 狐灵家族的种族 ID。任何这些种族被设为玩家当前种族时都会触发同步。 */
    private static final Set<ResourceLocation> FOX_SPIRIT_RACE_IDS = Set.of(
            id("baby_spirit_fox"),
            id("fox_spirit_envoy"),
            id("spirit_fox_contract_master"),
            id("mystic_fox_master"),
            id("heavenly_fox_sovereign")
    );

    private static volatile boolean registered = false;

    private SpiritSummonRaceSyncHandler() {}

    /**
     * 注册 SET_RACE 监听。幂等：重复调用安全。由 {@code TensuraTNOMod} 在模组初始化阶段调用。
     */
    public static void register() {
        if (registered) return;
        synchronized (SpiritSummonRaceSyncHandler.class) {
            if (registered) return;
            RaceEvents.SET_RACE.register((oldInstance, owner, newInstance, evolution, teleport, message) -> {
                if (!(owner instanceof ServerPlayer player)) return EventResult.pass();
                if (newInstance == null) return EventResult.pass();
                ResourceLocation newRaceId = newInstance.getRace().getRegistryName();
                if (newRaceId == null) return EventResult.pass();
                if (!FOX_SPIRIT_RACE_IDS.contains(newRaceId)) return EventResult.pass();

                int count = SpiritSummonSkill.SpiritSummonPockets.getAbsorbedEntities(player).size();
                newInstance.getOrCreateTag().putInt("absorbedTypesCount", count);
                return EventResult.pass();
            });
            registered = true;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, path);
    }
}
