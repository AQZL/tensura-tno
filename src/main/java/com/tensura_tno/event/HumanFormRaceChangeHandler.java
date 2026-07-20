package com.tensura_tno.event;

import com.tensura_tno.race.SlimeHumanFormState;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.race.api.RaceEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 种族切换清理处理器。
 *
 * <p>背景：人形状态技能是单次按键效果，注入的 SCALE 修饰器值由"按下瞬间的种族最终体型"
 * 决定。如果玩家用重置卷/转生从巨人种族切换到人类种族，原修饰器（针对巨人计算的 delta）
 * 会与新种族的体型属性叠加，导致新种族比正常的矮一截 —— 这种残留只能通过死亡或再次按键
 * 重新计算才能消除。
 *
 * <p>本处理器在 {@link RaceEvents#SET_RACE} 触发时立即移除该技能的修饰器，
 * 让新种族保持自身的默认体型。需要"人形"效果时，玩家可以再次按技能键重新施放。
 *
 * <p>幂等：可重复 {@link #register()}，事件只会注册一次。
 */
public final class HumanFormRaceChangeHandler {

    private static volatile boolean registered = false;

    private HumanFormRaceChangeHandler() {
    }

    public static void register() {
        if (registered) return;
        synchronized (HumanFormRaceChangeHandler.class) {
            if (registered) return;
            RaceEvents.SET_RACE.register((oldInstance, owner, newInstance, evolution, teleport, message) -> {
                if (owner instanceof ServerPlayer player) {
                    SlimeHumanFormState.onRaceChanged(player, newInstance);
                }
                return EventResult.pass();
            });
            registered = true;
        }
    }
}
