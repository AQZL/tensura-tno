package com.tensura_tno.ftb.rewards;

import com.tensura_tno.ftb.STExtrasHelper;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quest 奖励：为玩家增加指定数量的灵魂点数（Soul Grade）。
 */
public class SoulGradeReward extends Reward {

    public static RewardType SOUL_GRADE_REWARD;

    private int value = 20;

    public SoulGradeReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return SOUL_GRADE_REWARD;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        int current = STExtrasHelper.getSoulGrade(player);
        STExtrasHelper.setSoulGrade(player, current + value);
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putInt("value", value);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        value = nbt.getInt("value");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(value);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        value = buffer.readVarInt();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addInt("value", value, v -> value = v, 20, 1, Integer.MAX_VALUE)
              .setNameKey("tensura_tno.reward.soul_grade.value");
    }
}
