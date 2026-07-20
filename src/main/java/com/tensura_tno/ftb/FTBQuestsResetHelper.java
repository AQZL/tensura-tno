package com.tensura_tno.ftb;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.ftb.tasks.STExtrasQuestTask;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 负责在 STExtras 委托刷新时重置对应 FTB Quest 任务进度。
 * 此类被独立隔离，由 STExtrasQuestRerollMixin 通过反射调用，
 * 以保证在 FTBQuests 未安装时 Mixin 类本身不会因类加载失败而崩溃。
 */
public final class FTBQuestsResetHelper {

    /**
     * 当 STExtras 刷新玩家委托时调用：重置所有 autoReset=true 且类别匹配的 FTB 任务进度。
     *
     * @param player       玩家
     * @param rerollDaily  是否刷新了每日委托
     * @param rerollWeekly 是否刷新了每周委托
     */
    public static void onQuestReroll(ServerPlayer player, boolean rerollDaily, boolean rerollWeekly) {
        try {
            ServerQuestFile sf = ServerQuestFile.INSTANCE;
            if (sf == null) return;

            TeamData teamData = TeamData.get(player);
            if (teamData == null) return;

            sf.getAllTasks().forEach(task -> {
                if (!(task instanceof STExtrasQuestTask stTask)) return;
                if (!stTask.isAutoReset()) return;

                String cat = stTask.getCategory();
                boolean matches = (rerollDaily && "DAILY".equals(cat))
                        || (rerollWeekly && "WEEKLY".equals(cat));
                if (!matches) return;

                teamData.resetProgress(task);
            });
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.warn("[TNO] FTBQuests 委托任务重置失败: {}", e.getMessage());
        }
    }

    private FTBQuestsResetHelper() {}
}
