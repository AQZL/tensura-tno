package com.tensura_tno.race.fox_spirit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家死亡重生时，将灵之召唤收纳口袋数据从旧玩家复制到新玩家。
 * <p>
 * NeoForge 中玩家死亡后会创建新的 ServerPlayer 实例，
 * {@code getPersistentData()} 中的自定义数据不会自动复制，
 * 必须在 {@link PlayerEvent.Clone} 事件中手动迁移。
 */
public class SpiritSummonPocketPersistence {

    private static final String TAG_ROOT = "tensura_tno_spirit_summon";

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;

        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();

        CompoundTag oldData = oldPlayer.getPersistentData().getCompound(TAG_ROOT);
        if (!oldData.isEmpty()) {
            newPlayer.getPersistentData().put(TAG_ROOT, oldData.copy());
        }
    }
}
