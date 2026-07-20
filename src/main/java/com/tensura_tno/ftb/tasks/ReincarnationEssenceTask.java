package com.tensura_tno.ftb.tasks;

import com.tensura_tno.ftb.STExtrasHelper;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ISingleLongValueTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * FTB Quest 任务：检测玩家的 RE（转生精华）值是否达到目标数量。
 * 进度条实时显示当前 RE / 目标 RE。
 */
public class ReincarnationEssenceTask extends Task implements ISingleLongValueTask {

    public static TaskType RE_TASK;

    private long value = 500L;

    public ReincarnationEssenceTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return RE_TASK;
    }

    @Override
    public long getMaxProgress() {
        return value;
    }

    @Override
    public void setValue(long v) {
        value = v;
    }

    @Override
    public String formatMaxProgress() {
        return Long.toUnsignedString(value);
    }

    @Override
    public String formatProgress(TeamData teamData, long progress) {
        return Long.toUnsignedString(progress);
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putLong("value", value);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        value = nbt.getLong("value");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarLong(value);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        value = buffer.readVarLong();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addLong("value", value, v -> value = v, 500L, 1L, Long.MAX_VALUE)
              .setNameKey("tensura_tno.task.re.value");
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 20; // 每秒检查一次
    }

    @Override
    public void submitTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem) {
        if (!checkTaskSequence(teamData)) return;
        long currentRE = STExtrasHelper.getRE(player);
        teamData.setProgress(this, Math.min(currentRE, value));
    }
}
