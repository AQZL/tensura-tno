package com.tensura_tno.event;

import java.util.Set;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.race.SlimeHumanFormState;
import com.tensura_tno.race.SlimeRaceHelper;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 史莱姆种族玩家用「捕食者」或「暴食者」技能吞噬异世界人 / 玩家时，永久切换为人类形态。
 *
 * <p>触发条件（全部满足）：
 * <ol>
 *   <li>死者是 {@link Player} 或 tensura 异世界人系列实体</li>
 *   <li>杀手是 {@link Player}（直接命中或弹幕命中归因）</li>
 *   <li>杀手当前种族属于 slime 家族（slime / metal_slime / demon_slime / god_slime）</li>
 *   <li>杀手身上拥有 {@code tensura:predator} 或 {@code tensura:gluttony} 技能</li>
 * </ol>
 *
 * <p>满足后<b>立即调用 {@link HumanFormSkill#applyHumanForm}</b>，注入 SCALE=1 的
 * permanent modifier。这个 modifier 是 vanilla attribute 系统持久化的，会：
 * <ul>
 *   <li>自动从服务端同步到所有看到该玩家的客户端 → 渲染立即切回人形</li>
 *   <li>保存到玩家 NBT → 退出再登录依然有效</li>
 *   <li>{@link com.tensura_tno.client.race.PlayerSlimeRenderManager#shouldRenderAsSlime}
 *       检测到该 modifier 后会跳过 slime 模型替换</li>
 * </ul>
 *
 * <p>注意：转生 / 重置卷会通过 {@link com.tensura_tno.event.HumanFormRaceChangeHandler}
 * 清掉这个 modifier，这是合理的（转生本来就重置一切）。
 */
public final class SlimePredationHumanFormHandler {

    /**
     * 异世界人 entity ID 白名单（来自 tensura {@code HumanEntityTypes}）：
     * 排除 dwarf / gazel / falmuth_knight / bone_golem / clone / training_dummy / skeleton / zombie，
     * 只保留真正"异世界来访者"的角色，与 race_prestige 任务里的"异世界人"语义一致。
     */
    private static final Set<ResourceLocation> OTHERWORLDER_IDS = Set.of(
            id("folgen"),
            id("hinata_sakaguchi"),
            id("kirara_mizutani"),
            id("kyoya_tachibana"),
            id("mai_furuki"),
            id("mark_lauren"),
            id("shinji_tanimura"),
            id("shin_ryusei"),
            id("shizu"),
            id("shogo_taguchi")
    );

    private static volatile boolean registered = false;

    private SlimePredationHumanFormHandler() {}

    /** 注册到 NeoForge 事件总线。由主类 {@code TensuraTNOMod} 调用。 */
    public static void register() {
        if (registered) return;
        synchronized (SlimePredationHumanFormHandler.class) {
            if (registered) return;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(SlimePredationHumanFormHandler.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        // 死者必须是玩家或异世界人
        if (!isOtherworlderOrPlayer(victim)) return;

        // 杀手归因：直接源头
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player playerKiller)) return;
        if (playerKiller == victim) return;  // 自杀不算
        if (!(playerKiller instanceof ServerPlayer server)) return;

        // 已经是人形（之前吞噬过 / 主动按了人形技能），不重复触发
        // 必须是 slime 种族（其他种族吞噬不触发）
        if (!SlimeRaceHelper.isSlimeRace(server)) return;

        // 必须拥有捕食者或暴食者
        if (!hasPredationSkill(server)) return;

        // 全部条件满足：注入永久人形 modifier。attribute 系统会自动同步到客户端，
        // 玩家立即从 slime 模型变回人形。
        SlimeHumanFormState.unlock(server);

        TensuraTNOMod.LOGGER.info(
                "[TensuraTNO] Slime player {} unlocked human-form toggle (devoured {})",
                server.getGameProfile().getName(),
                victim.getType().builtInRegistryHolder().key().location());
    }

    private static boolean isOtherworlderOrPlayer(LivingEntity victim) {
        if (victim instanceof Player) return true;
        EntityType<?> type = victim.getType();
        ResourceLocation id = type.builtInRegistryHolder().key().location();
        return OTHERWORLDER_IDS.contains(id);
    }

    private static boolean hasPredationSkill(LivingEntity entity) {
        try {
            ManasSkill predator = UniqueSkills.PREDATOR.get();
            if (predator != null && SkillUtils.hasSkill(entity, predator)) return true;
        } catch (Throwable ignored) {}
        try {
            ManasSkill gluttony = UniqueSkills.GLUTTONY.get();
            if (gluttony != null && SkillUtils.hasSkill(entity, gluttony)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tensura", path);
    }
}
