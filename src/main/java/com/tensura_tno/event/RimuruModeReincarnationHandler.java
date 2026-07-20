package com.tensura_tno.event;

import java.util.Optional;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.network.RimuruModePackets;

import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.race.api.RaceEvents;
import io.github.manasmods.manascore.race.api.Races;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 利姆露模式转生处理器：当玩家在转生 GUI 勾选了「利姆露模式」并选择史莱姆种族时，
 * 自动学会 {@link ReincarnationMenu#RIMURU_SKILLS} 列表（捕食者、大贤者、各类抗性等），
 * 并把可学技能加为种族 intrinsic（与 {@link ReincarnationMenu#reincarnateAsRimuru}
 * 后半部分逻辑一致）。
 *
 * <p>触发条件（全部满足）：
 * <ol>
 *   <li>玩家是 {@link ServerPlayer}</li>
 *   <li>新种族 ID 是 {@code tensura:slime}（仅 base slime，不含 metal/demon/god）</li>
 *   <li>玩家 PersistentData 中有 {@link RimuruModePackets#NBT_PENDING_RIMURU_MODE}=true</li>
 * </ol>
 *
 * <p>触发后清掉 pending 标记，避免之后再选 slime 时重复。
 */
public final class RimuruModeReincarnationHandler {

    /** 仅 base slime 触发（与 vanilla {@code reincarnateAsRimuru} 一致）。 */
    private static final ResourceLocation SLIME_RACE_ID =
            ResourceLocation.fromNamespaceAndPath("tensura", "slime");

    private static volatile boolean registered = false;

    private RimuruModeReincarnationHandler() {}

    /** 注册 SET_RACE 监听。幂等：重复调用安全。由主类初始化阶段调用。 */
    public static void register() {
        if (registered) return;
        synchronized (RimuruModeReincarnationHandler.class) {
            if (registered) return;
            RaceEvents.SET_RACE.register((oldInstance, owner, newInstance, evolution, teleport, message) -> {
                if (!(owner instanceof ServerPlayer player)) return EventResult.pass();
                if (newInstance == null) return EventResult.pass();

                ResourceLocation newRaceId = newInstance.getRace().getRegistryName();
                if (newRaceId == null) return EventResult.pass();
                if (!SLIME_RACE_ID.equals(newRaceId)) return EventResult.pass();

                if (!player.getPersistentData().getBoolean(RimuruModePackets.NBT_PENDING_RIMURU_MODE)) {
                    return EventResult.pass();
                }

                // 服务端额外防御：仅专属服启用 config 门控；单人/集成服(含 LAN host)绕过。
                // 与 RimuruModePackets#handleSetPending、客户端 mixin 判据保持一致。
                net.minecraft.server.MinecraftServer srv = player.getServer();
                boolean dedicated = srv != null && srv.isDedicatedServer();
                if (dedicated && !com.tensura_tno.TensuraTNOCompatConfig.isRimuruModeCheckboxEnabled()) {
                    player.getPersistentData().remove(RimuruModePackets.NBT_PENDING_RIMURU_MODE);
                    return EventResult.pass();
                }

                // 防御：如果世界规则已开启 RIMURU_MODE，vanilla 已处理（reincarnateAsRimuru
                // 在 checkForFirstLogin/ResetScrollItem 里直接调），我们不重复执行。
                try {
                    if (player.level().getGameRules().getBoolean(TensuraGameRules.RIMURU_MODE)) {
                        player.getPersistentData().remove(RimuruModePackets.NBT_PENDING_RIMURU_MODE);
                        return EventResult.pass();
                    }
                } catch (Throwable ignored) {
                    // gamerule 读不到时不阻断主逻辑
                }

                // 命中：清标记 + 学习 RIMURU_SKILLS
                player.getPersistentData().remove(RimuruModePackets.NBT_PENDING_RIMURU_MODE);
                grantRimuruSkills(player);

                return EventResult.pass();
            });
            registered = true;
        }
    }

    /**
     * 复刻 {@link ReincarnationMenu#reincarnateAsRimuru} 后半段逻辑：
     * 学习 {@code RIMURU_SKILLS} 列表，并把可学技能加为种族 intrinsic。
     */
    private static void grantRimuruSkills(ServerPlayer player) {
        try {
            for (ManasSkill skill : ReincarnationMenu.RIMURU_SKILLS) {
                TensuraSkillInstance instance = new TensuraSkillInstance(skill);
                instance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
                if (SkillHelper.learnSkill(player, instance)
                        && !ReincarnationMenu.RIMURU_UNIQUE_SKILLS.contains(skill)) {
                    Races races = RaceAPI.getRaceFrom(player);
                    Optional<ManasRaceInstance> optional = races.getRace();
                    if (optional.isPresent()) {
                        optional.get().addIntrinsicSkill(skill);
                        optional.get().markDirty();
                        races.markDirty();
                    }
                }
            }
            TensuraTNOMod.LOGGER.info(
                    "[TensuraTNO] Granted Rimuru-mode skills to {}",
                    player.getGameProfile().getName());
        } catch (Throwable t) {
            TensuraTNOMod.LOGGER.error(
                    "[TensuraTNO] Failed to grant Rimuru-mode skills to {}: {}",
                    player.getGameProfile().getName(), t.toString());
        }
    }
}
