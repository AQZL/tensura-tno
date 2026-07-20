package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.registry.TensuraTNORaces;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * 狐灵种族召唤物数值加成及灵之召唤配置工具类。
 * <p>
 * 根据玩家当前的狐灵种族进化阶段，提供：
 * <ul>
 *   <li>召唤物 HP / 攻击力百分比加成</li>
 *   <li>灵之召唤最大同时召唤数</li>
 *   <li>灵之召唤物存在时长</li>
 * </ul>
 * <p>
 * 阶段配置：
 * <table>
 *   <tr><th>种族</th><th>HP/ATK加成</th><th>召唤上限</th><th>存在时长</th></tr>
 *   <tr><td>幼灵狐</td><td>+0%</td><td>3</td><td>3分钟</td></tr>
 *   <tr><td>狐灵使</td><td>+10%</td><td>8</td><td>6分钟</td></tr>
 *   <tr><td>灵狐契主</td><td>+25%</td><td>14</td><td>9分钟</td></tr>
 *   <tr><td>玄灵狐主</td><td>+40%</td><td>20</td><td>15分钟</td></tr>
 *   <tr><td>天灵狐尊</td><td>+50%</td><td>30</td><td>27分钟</td></tr>
 * </table>
 */
public final class FoxSpiritSummonBonus {

    /** 灵之召唤基础存在时长（秒）：幼灵狐阶段的基础值 */
    private static final int BASE_SUMMON_DURATION = 300; // 5分钟

    private FoxSpiritSummonBonus() {
    }

    /**
     * 获取玩家召唤物的 HP / 伤害加成比例。
     *
     * @return 加成比例，如 0.1 表示 +10%；非狐灵种族返回 0.0
     */
    public static float getBonusPercentage(Player player) {
        Optional<ManasRaceInstance> raceOpt = RaceAPI.getRaceFrom(player).getRace();
        if (raceOpt.isEmpty()) return 0.0F;

        ManasRace race = raceOpt.get().getRace();
        if (race == null) return 0.0F;

        // 按种族注册名判定加成等级
        ResourceLocation raceId = race.getRegistryName();

        if (raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "heavenly_fox_sovereign")))  return 0.50F;  // +50%
        if (raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "mystic_fox_master")))        return 0.40F;  // +40%
        if (raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "spirit_fox_contract_master"))) return 0.25F;  // +25%
        if (raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "fox_spirit_envoy")))         return 0.10F;  // +10%
        if (raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "baby_spirit_fox")))          return 0.00F;  // +0%

        return 0.0F; // 非狐灵种族无加成
    }

    /**
     * 判断玩家是否属于狐灵种族线（任何阶段）。
     */
    public static boolean isFoxSpiritRace(Player player) {
        Optional<ManasRaceInstance> raceOpt = RaceAPI.getRaceFrom(player).getRace();
        if (raceOpt.isEmpty()) return false;

        ManasRace race = raceOpt.get().getRace();
        if (race == null) return false;

        ResourceLocation raceId = race.getRegistryName();
        ResourceLocation prefix = ResourceLocation.fromNamespaceAndPath("tensura_tno", "");
        return raceId.getNamespace().equals("tensura_tno")
                && (raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "baby_spirit_fox"))
                || raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "fox_spirit_envoy"))
                || raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "spirit_fox_contract_master"))
                || raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "mystic_fox_master"))
                || raceId.equals(ResourceLocation.fromNamespaceAndPath("tensura_tno", "heavenly_fox_sovereign")));
    }

    /**
     * 应用加成：在基础值上叠加百分比。
     *
     * @param baseValue 基础数值
     * @param bonus     加成比例（如 0.25 = +25%）
     * @return 加成后的数值
     */
    public static double applyBonus(double baseValue, float bonus) {
        return baseValue * (1.0 + bonus);
    }

    // ── 灵之召唤种族阶段配置 ──

    /**
     * 获取玩家灵之召唤的最大同时召唤数。
     *
     * @return 召唤上限；非狐灵种族返回 0（不允许召唤）
     */
    public static int getMaxSummons(Player player) {
        ResourceLocation raceId = getRaceId(player);
        if (raceId == null) return 0;

        if (raceId.equals(RL("heavenly_fox_sovereign")))  return 30;
        if (raceId.equals(RL("mystic_fox_master")))        return 20;
        if (raceId.equals(RL("spirit_fox_contract_master"))) return 14;
        if (raceId.equals(RL("fox_spirit_envoy")))         return 8;
        if (raceId.equals(RL("baby_spirit_fox")))          return 3;

        return 0; // 非狐灵种族不允许召唤
    }

    /**
     * 获取玩家灵之召唤的召唤物存在时长（秒）。
     * <p>
     * 幼灵狐基础 3 分钟，每高一阶额外增加时长：
     * <ul>
     *   <li>幼灵狐: 180s (3min)</li>
     *   <li>狐灵使: 360s (6min = 基础+3min)</li>
     *   <li>灵狐契主: 540s (9min = 基础+6min)</li>
     *   <li>玄灵狐主: 900s (15min = 基础+12min)</li>
     *   <li>天灵狐尊: 1620s (27min = 基础+24min)</li>
     * </ul>
     *
     * @return 存在时长（秒）；非狐灵种族返回 BASE_SUMMON_DURATION
     */
    public static int getSummonDurationSeconds(Player player) {
        ResourceLocation raceId = getRaceId(player);
        if (raceId == null) return BASE_SUMMON_DURATION;

        if (raceId.equals(RL("heavenly_fox_sovereign")))  return BASE_SUMMON_DURATION + 1440; // +24min
        if (raceId.equals(RL("mystic_fox_master")))        return BASE_SUMMON_DURATION + 720;  // +12min
        if (raceId.equals(RL("spirit_fox_contract_master"))) return BASE_SUMMON_DURATION + 360; // +6min
        if (raceId.equals(RL("fox_spirit_envoy")))         return BASE_SUMMON_DURATION + 180;  // +3min
        if (raceId.equals(RL("baby_spirit_fox")))          return BASE_SUMMON_DURATION;

        return BASE_SUMMON_DURATION;
    }

    /**
     * 获取狐灵种族进化等级（0~4），非狐灵种族返回 -1。
     * <ul>
     *   <li>0 = 幼灵狐</li>
     *   <li>1 = 狐灵使</li>
     *   <li>2 = 灵狐契主</li>
     *   <li>3 = 玄灵狐主</li>
     *   <li>4 = 天灵狐尊</li>
     * </ul>
     */
    public static int getEvolutionLevel(Player player) {
        ResourceLocation raceId = getRaceId(player);
        if (raceId == null) return -1;
        if (raceId.equals(RL("heavenly_fox_sovereign")))  return 4;
        if (raceId.equals(RL("mystic_fox_master")))        return 3;
        if (raceId.equals(RL("spirit_fox_contract_master"))) return 2;
        if (raceId.equals(RL("fox_spirit_envoy")))         return 1;
        if (raceId.equals(RL("baby_spirit_fox")))          return 0;
        return -1;
    }

    // ── 内部工具 ──

    private static @org.jetbrains.annotations.Nullable ResourceLocation getRaceId(Player player) {
        Optional<ManasRaceInstance> raceOpt = RaceAPI.getRaceFrom(player).getRace();
        if (raceOpt.isEmpty()) return null;
        ManasRace race = raceOpt.get().getRace();
        return race != null ? race.getRegistryName() : null;
    }

    private static ResourceLocation RL(String path) {
        return ResourceLocation.fromNamespaceAndPath("tensura_tno", path);
    }
}
