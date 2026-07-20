package com.tensura_tno.ftb.tasks;

import com.tensura_tno.ftb.STExtrasHelper;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import io.github.manasmods.tensura.item.misc.ResetScrollItem;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quest 任务：检测玩家是否满足 Tensura 的 Reset Counter 条件。
 */
public class ResetCounterTask extends AbstractBooleanTask {

    public static TaskType RESET_COUNTER_TASK;

    public ResetCounterTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return RESET_COUNTER_TASK;
    }

    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        if (player == null || teamData == null || !checkTaskSequence(teamData)) return false;
        if (STExtrasHelper.isLoaded()) {
            return STExtrasHelper.canPrestige(player);
        }
        return ResetScrollItem.isFullReset(player);
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 60;
    }
}
