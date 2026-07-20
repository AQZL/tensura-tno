package com.tensura_tno.race;

import com.tensura_tno.ability.skill.HumanFormSkill;
import com.tensura_tno.network.SlimeHumanFormPackets;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class SlimeHumanFormState {
    private static final String TAG_ROOT = "tensura_tno_slime_human_form";
    private static final String TAG_UNLOCKED = "Unlocked";
    private static final String TAG_CHECKED = "Checked";

    private SlimeHumanFormState() {
    }

    public static boolean isUnlocked(Player player) {
        if (player == null) return false;
        return getRoot(player).getBoolean(TAG_UNLOCKED);
    }

    public static boolean isChecked(Player player) {
        if (!isUnlocked(player)) return false;
        CompoundTag root = getRoot(player);
        return !root.contains(TAG_CHECKED, Tag.TAG_BYTE) || root.getBoolean(TAG_CHECKED);
    }

    public static void unlock(ServerPlayer player) {
        CompoundTag root = getOrCreateRoot(player);
        boolean wasUnlocked = root.getBoolean(TAG_UNLOCKED);
        root.putBoolean(TAG_UNLOCKED, true);
        if (!wasUnlocked || !root.contains(TAG_CHECKED, Tag.TAG_BYTE)) {
            root.putBoolean(TAG_CHECKED, true);
        }
        player.getPersistentData().put(TAG_ROOT, root);
        applyCurrentSelection(player);
        SlimeHumanFormPackets.syncToClient(player);
    }

    public static void setChecked(ServerPlayer player, boolean checked) {
        if (!isUnlocked(player) || !SlimeRaceHelper.isSlimeRace(player)) {
            SlimeHumanFormPackets.syncToClient(player);
            return;
        }

        CompoundTag root = getOrCreateRoot(player);
        root.putBoolean(TAG_CHECKED, checked);
        player.getPersistentData().put(TAG_ROOT, root);
        applyCurrentSelection(player);
        SlimeHumanFormPackets.syncToClient(player);
    }

    public static void applyCurrentSelection(ServerPlayer player) {
        if (!isUnlocked(player)) return;

        if (isChecked(player) && SlimeRaceHelper.isSlimeRace(player)) {
            HumanFormSkill.applyHumanForm(player);
        } else {
            HumanFormSkill.removeHumanForm(player);
        }
    }

    public static void onRaceChanged(ServerPlayer player, ManasRaceInstance newInstance) {
        HumanFormSkill.removeHumanForm(player);

        if (isUnlocked(player) && isChecked(player) && isSlimeRace(newInstance)) {
            var server = player.getServer();
            if (server != null) {
                server.execute(() -> {
                    applyCurrentSelection(player);
                    SlimeHumanFormPackets.syncToClient(player);
                });
                return;
            }
            applyCurrentSelection(player);
        }
        SlimeHumanFormPackets.syncToClient(player);
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();

        CompoundTag oldData = oldPlayer.getPersistentData().getCompound(TAG_ROOT);
        if (!oldData.isEmpty()) {
            newPlayer.getPersistentData().put(TAG_ROOT, oldData.copy());
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyCurrentSelection(player);
            SlimeHumanFormPackets.syncToClient(player);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyCurrentSelection(player);
            SlimeHumanFormPackets.syncToClient(player);
        }
    }

    private static boolean isSlimeRace(ManasRaceInstance instance) {
        if (instance == null || instance.getRace() == null) return false;
        ResourceLocation id = instance.getRace().getRegistryName();
        return id != null && SlimeRaceHelper.SLIME_RACE_IDS.contains(id);
    }

    private static CompoundTag getRoot(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(TAG_ROOT, Tag.TAG_COMPOUND) ? data.getCompound(TAG_ROOT) : new CompoundTag();
    }

    private static CompoundTag getOrCreateRoot(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag root = data.contains(TAG_ROOT, Tag.TAG_COMPOUND)
                ? data.getCompound(TAG_ROOT)
                : new CompoundTag();
        data.put(TAG_ROOT, root);
        return root;
    }
}
