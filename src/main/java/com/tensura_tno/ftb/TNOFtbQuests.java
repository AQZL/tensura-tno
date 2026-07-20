package com.tensura_tno.ftb;

import com.tensura_tno.ftb.rewards.ReincarnationEssenceReward;
import com.tensura_tno.ftb.rewards.SoulGradeReward;
import com.tensura_tno.ftb.rewards.SoulEnergyReward;
import com.tensura_tno.ftb.tasks.ReincarnationEssenceTask;
import com.tensura_tno.ftb.tasks.ResetCounterTask;
import com.tensura_tno.ftb.tasks.STExtrasQuestTask;
import com.tensura_tno.ftb.tasks.SoulGradeTask;
import com.tensura_tno.ftb.tasks.TaskTableItemTask;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.resources.ResourceLocation;

/**
 * 注册本 mod 的 FTB Quests 自定义任务与奖励类型。
 * 在 FTBQuestsAPI / TaskTypes 可用之后调用（FTBQuests 加载后）。
 */
public final class TNOFtbQuests {

    public static final String MOD_ID = "tensura_tno";

    public static void init() {
        // ---- 任务 ----
        ReincarnationEssenceTask.RE_TASK = TaskTypes.register(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "re_value"),
                ReincarnationEssenceTask::new,
                Icon::empty
        );

        STExtrasQuestTask.STEXTRAS_QUEST = TaskTypes.register(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "stextras_quest"),
                STExtrasQuestTask::new,
                Icon::empty
        );

        SoulGradeTask.SOUL_GRADE_TASK = TaskTypes.register(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "soul_grade"),
                SoulGradeTask::new,
                Icon::empty
        );

        ResetCounterTask.RESET_COUNTER_TASK = TaskTypes.register(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "reset_counter"),
                ResetCounterTask::new,
                () -> Icon.getIcon("tensura:textures/gui/reset_medals/stone_reset_medal.png")
        );

        TaskTableItemTask.TASK_TABLE_ITEM_TASK = TaskTypes.register(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "task_table_item"),
                TaskTableItemTask::new,
                () -> Icon.getIcon("ftblibrary:textures/icons/money_bag.png")
        );

        // ---- 奖励 ----
        ReincarnationEssenceReward.RE_REWARD = RewardTypes.register(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "re_value"),
                ReincarnationEssenceReward::new,
                Icon::empty
        );

        SoulGradeReward.SOUL_GRADE_REWARD = RewardTypes.register(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "soul_grade"),
                SoulGradeReward::new,
                Icon::empty
        );

        SoulEnergyReward.SOUL_ENERGY_REWARD = RewardTypes.register(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "soul_energy"),
                SoulEnergyReward::new,
                Icon::empty
        );
    }

    private TNOFtbQuests() {}
}
