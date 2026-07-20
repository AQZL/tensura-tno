package com.tensura_tno.ftb.tasks;

import com.tensura_tno.ftb.STExtrasHelper;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;

/**
 * FTB Quest 任务：检测玩家是否完成了 STExtras 指定类别的所有委托。
 *
 * <ul>
 *   <li>category：DAILY（每日）/ WEEKLY（每周）/ REQUIRED（必备），默认 DAILY</li>
 *   <li>autoReset：true 时，当 STExtras 刷新对应委托时，此任务进度自动重置（可再次完成）</li>
 * </ul>
 */
public class STExtrasQuestTask extends AbstractBooleanTask {

    public static TaskType STEXTRAS_QUEST;

    /** 委托类别：DAILY / WEEKLY / REQUIRED */
    private String category = "DAILY";

    /** 当 STExtras 委托刷新时是否自动重置此任务进度 */
    private boolean autoReset = true;

    public STExtrasQuestTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return STEXTRAS_QUEST;
    }

    /**
     * 当玩家该类别的活跃委托为空、且至少有一条已完成委托时，任务成立。
     *
     * <p>仅检查"活跃为空"是不够的：新世界玩家尚未被分配任何委托，
     * 活跃列表同样是空集，若不加已完成条件则会立即误判为全部完成。
     */
    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        if (player == null) return false;
        if (!STExtrasHelper.isLoaded()) return false;
        Set<ResourceLocation> active    = STExtrasHelper.getActiveQuests(player, category);
        Set<ResourceLocation> completed = STExtrasHelper.getCompletedQuests(player, category);
        // active / completed 为 null 表示 API 调用出错，不应视为完成
        if (active == null || completed == null) return false;
        return active.isEmpty() && !completed.isEmpty();
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("category", category);
        nbt.putBoolean("autoReset", autoReset);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        String cat = nbt.getString("category");
        category = cat.isEmpty() ? "DAILY" : cat;
        autoReset = nbt.getBoolean("autoReset");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(category);
        buffer.writeBoolean(autoReset);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        category = buffer.readUtf();
        autoReset = buffer.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // 类别选择：DAILY / WEEKLY / REQUIRED
        config.addString("category", category, v -> category = v, "DAILY")
              .setNameKey("tensura_tno.task.stextras_quest.category");
        // 是否跟随委托刷新自动重置
        config.addBool("autoReset", autoReset, v -> autoReset = v, true)
              .setNameKey("tensura_tno.task.stextras_quest.auto_reset");
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 20; // 每秒检查一次委托是否全部完成
    }

    public String getCategory() {
        return category;
    }

    public boolean isAutoReset() {
        return autoReset;
    }
}
