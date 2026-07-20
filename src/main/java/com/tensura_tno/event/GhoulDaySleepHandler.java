package com.tensura_tno.event;

import java.util.Optional;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.tensura.registry.race.TensuraRaces;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * 食尸鬼系列种族白天睡觉功能。
 * <p>
 * 食尸鬼（Ghoul）、吸血鬼（Vampire）、吸血鬼超越者（Vampire Overcomer）、
 * 吸血鬼之王（Vampire Lord）、神祖吸血鬼（Divine Vampire）在白天受到虚弱等
 * 负面效果，因此允许它们白天使用床睡觉，跳过到夜晚。
 */
public final class GhoulDaySleepHandler {

    private GhoulDaySleepHandler() {}

    /**
     * 判断玩家是否属于白天受debuff的亡灵系种族（食尸鬼系 + 白骨系）。
     */
    private static boolean isSunWeakRace(Player player) {
        Optional<ManasRaceInstance> opt = RaceAPI.getRaceFrom(player).getRace();
        if (opt.isEmpty()) return false;
        ManasRace race = opt.get().getRace();
        return race == TensuraRaces.GHOUL.get()
            || race == TensuraRaces.VAMPIRE.get()
            || race == TensuraRaces.VAMPIRE_OVERCOMER.get()
            || race == TensuraRaces.VAMPIRE_LORD.get()
            || race == TensuraRaces.DIVINE_VAMPIRE.get()
            || race == TensuraRaces.WIGHT.get()
            || race == TensuraRaces.WIGHT_KING.get();
    }

    /**
     * 允许食尸鬼系种族在白天使用床。
     * <p>
     * 当 vanilla 返回 {@code NOT_POSSIBLE_NOW}（白天不能睡觉）且玩家属于
     * 食尸鬼系种族时，清除该限制。其他问题（怪物、距离等）保持不变。
     */
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
        if (!isSunWeakRace(event.getEntity())) return;
        event.setProblem(null);
    }

    /**
     * 允许食尸鬼系种族在白天持续睡觉。
     * <p>
     * {@code Player.tick()} 每 tick 检查睡觉条件，白天会产生
     * {@code NOT_POSSIBLE_NOW} 问题并立即唤醒玩家。
     * 对食尸鬼系种族强制允许继续睡觉。
     */
    public static void onCanContinueSleeping(CanContinueSleepingEvent event) {
        if (event.getProblem() != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isSunWeakRace(player)) return;
        event.setContinueSleeping(true);
    }

    /**
     * 当白天睡觉结束时，将时间推进到夜晚而非清晨。
     * <p>
     * 正常情况下 vanilla 只在夜晚允许睡觉，因此 {@code SleepFinishedTimeEvent}
     * 只在夜晚触发。如果该事件在白天触发，说明是食尸鬼系种族白天睡觉，
     * 我们将目标时间改为当天的 13000（夜幕降临）。
     */
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long dayTime = level.getDayTime();
        long dayOffset = dayTime % 24000L;
        // 仅在白天（dayOffset < 13000）时修改目标时间
        if (dayOffset >= 13000L) return;
        long nightTarget = dayTime - dayOffset + 13000L;
        event.setTimeAddition(nightTarget);
    }
}
