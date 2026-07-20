package com.tensura_tno.ftb;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import com.tensura_tno.TensuraTNOMod;

import dev.architectury.registry.registries.Registrar;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.tensura.ability.battlewill.Battlewill;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 通过反射访问 STExtras 的委托系统，在种族威望任务激活时
 * 回溯检测玩家已精通的技能，修复 MASTER 类任务无法完成的 Bug。
 *
 * <p>所有 STExtras 类通过反射访问，避免编译期对 STExtras jar 的强依赖。
 * 若 STExtras 未加载，所有操作静默跳过。</p>
 */
public final class MasterySeedHelper {

    // --- STExtras 反射句柄 ---
    private static boolean loaded = false;

    // ISTExtrasQuests 接口 → stextras$getQuests() → STExtrasQuestStorage
    private static Method GET_QUESTS;

    // STExtrasQuestStorage 方法
    private static Method STORE_ACTIVE;       // active(QuestCategory) → Set<RL>
    private static Method STORE_COMPLETED;    // completed(QuestCategory) → Set<RL>
    private static Method STORE_MARK_COMPLETED; // markCompleted(RL)
    private static Method STORE_HAS_COUNTED;  // hasCounted(RL, String) → boolean
    private static Method STORE_ADD_COUNTED;  // addCounted(RL, String)
    private static Method STORE_ADD_PROGRESS; // addProgressAndMaybeComplete(RL, int, int) → boolean

    // QuestRegistry.INSTANCE.byCategory(QuestCategory) → List<QuestDefinition>
    private static Object QUEST_REGISTRY_INSTANCE;
    private static Method REGISTRY_BY_CATEGORY;

    // QuestCategory.RACE_PRESTIGE
    private static Object CAT_RACE_PRESTIGE;

    // QuestDefinition 字段
    private static Field QD_ID;         // ResourceLocation
    private static Field QD_CREATOR;    // QuestObjectiveType (enum)
    private static Field QD_TYPES;      // List<ResourceLocation>
    private static Field QD_AMOUNT;     // int

    // QuestObjectiveType 枚举常量
    private static Object QOT_MASTER;
    private static Object QOT_MASTER_TYPE;
    private static Object QOT_MASTER_TYPE_MAGIC;
    private static Object QOT_MASTER_SUBTYPE_MAGIC;

    // TensuraMasteryTypeIds 方法
    private static Method SKILL_TYPE_ID;    // skillTypeId(Skill.SkillType) → RL
    private static Method BATTLEWILL_ID;    // battlewillId() → RL
    private static Method MAGIC_TYPE_ID;    // magicTypeId(Magic.MagicType) → RL
    private static Method MAGIC_SUBTYPE_IDS; // magicSubtypeIds(Magic) → Set<RL>

    static {
        try {
            // ISTExtrasQuests → stextras$getQuests()
            Class<?> iQuests = Class.forName("org.crypticdev.stextras.storage.quest.ISTExtrasQuests");
            GET_QUESTS = iQuests.getMethod("stextras$getQuests");

            // STExtrasQuestStorage
            Class<?> storeCls = Class.forName("org.crypticdev.stextras.storage.quest.STExtrasQuestStorage");
            Class<?> catCls = Class.forName("org.crypticdev.stextras.quest.definition.QuestCategory");
            STORE_ACTIVE = storeCls.getMethod("active", catCls);
            STORE_COMPLETED = storeCls.getMethod("completed", catCls);
            STORE_MARK_COMPLETED = storeCls.getMethod("markCompleted", ResourceLocation.class);
            STORE_HAS_COUNTED = storeCls.getMethod("hasCounted", ResourceLocation.class, String.class);
            STORE_ADD_COUNTED = storeCls.getMethod("addCounted", ResourceLocation.class, String.class);
            STORE_ADD_PROGRESS = storeCls.getMethod("addProgressAndMaybeComplete", ResourceLocation.class, int.class, int.class);

            // QuestCategory.RACE_PRESTIGE
            @SuppressWarnings("unchecked")
            Class<Enum> catEnum = (Class<Enum>) catCls;
            CAT_RACE_PRESTIGE = Enum.valueOf(catEnum, "RACE_PRESTIGE");

            // QuestRegistry.INSTANCE
            Class<?> regCls = Class.forName("org.crypticdev.stextras.quest.registry.QuestRegistry");
            Field instanceField = regCls.getField("INSTANCE");
            QUEST_REGISTRY_INSTANCE = instanceField.get(null);
            REGISTRY_BY_CATEGORY = regCls.getMethod("byCategory", catCls);

            // QuestDefinition fields
            Class<?> qdCls = Class.forName("org.crypticdev.stextras.quest.definition.QuestDefinition");
            QD_ID = qdCls.getField("id");
            QD_CREATOR = qdCls.getField("creator");
            QD_TYPES = qdCls.getField("types");
            QD_AMOUNT = qdCls.getField("amount");

            // QuestObjectiveType enum constants
            Class<?> qotCls = Class.forName("org.crypticdev.stextras.quest.definition.QuestObjectiveType");
            @SuppressWarnings("unchecked")
            Class<Enum> qotEnum = (Class<Enum>) qotCls;
            QOT_MASTER = Enum.valueOf(qotEnum, "MASTER");
            QOT_MASTER_TYPE = Enum.valueOf(qotEnum, "MASTER_TYPE");
            QOT_MASTER_TYPE_MAGIC = Enum.valueOf(qotEnum, "MASTER_TYPE_MAGIC");
            QOT_MASTER_SUBTYPE_MAGIC = Enum.valueOf(qotEnum, "MASTER_SUBTYPE_MAGIC");

            // TensuraMasteryTypeIds
            Class<?> tmCls = Class.forName("org.crypticdev.stextras.quest.definition.TensuraMasteryTypeIds");
            SKILL_TYPE_ID = tmCls.getMethod("skillTypeId", Skill.SkillType.class);
            BATTLEWILL_ID = tmCls.getMethod("battlewillId");
            MAGIC_TYPE_ID = tmCls.getMethod("magicTypeId", Magic.MagicType.class);
            MAGIC_SUBTYPE_IDS = tmCls.getMethod("magicSubtypeIds", Magic.class);

            loaded = true;
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.warn("[TNO] MasterySeedHelper 初始化失败（STExtras 未加载？）: {}", e.getMessage());
        }
    }

    /**
     * 在种族威望任务激活后，检查玩家已精通的技能并回溯设置进度。
     */
    public static void seedMasteryQuests(ServerPlayer player) {
        if (!loaded) return;
        try {
            Object store = GET_QUESTS.invoke(player);
            if (store == null) return;

            @SuppressWarnings("unchecked")
            Set<ResourceLocation> active = (Set<ResourceLocation>) STORE_ACTIVE.invoke(store, CAT_RACE_PRESTIGE);
            @SuppressWarnings("unchecked")
            Set<ResourceLocation> completed = (Set<ResourceLocation>) STORE_COMPLETED.invoke(store, CAT_RACE_PRESTIGE);

            @SuppressWarnings("unchecked")
            List<Object> raceQuests = (List<Object>) REGISTRY_BY_CATEGORY.invoke(QUEST_REGISTRY_INSTANCE, CAT_RACE_PRESTIGE);

            Registrar<ManasSkill> reg = SkillAPI.getSkillRegistry();
            Skills skills = SkillAPI.getSkillsFrom(player);

            for (Object quest : raceQuests) {
                ResourceLocation qId = (ResourceLocation) QD_ID.get(quest);
                if (!active.contains(qId) || completed.contains(qId)) continue;

                Object creator = QD_CREATOR.get(quest);
                if (creator.equals(QOT_MASTER)) {
                    seedMaster(player, store, quest, qId, reg);
                } else if (creator.equals(QOT_MASTER_TYPE)) {
                    seedMasterType(player, store, quest, qId, reg, skills);
                } else if (creator.equals(QOT_MASTER_TYPE_MAGIC)) {
                    seedMasterTypeMagic(player, store, quest, qId, reg, skills);
                } else if (creator.equals(QOT_MASTER_SUBTYPE_MAGIC)) {
                    seedMasterSubtypeMagic(player, store, quest, qId, reg, skills);
                }
            }
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.warn("[TNO] MasterySeedHelper.seedMasteryQuests 出错: {}", e.getMessage());
        }
    }

    /** MASTER 任务（无数量要求）：检查特定技能是否已精通。 */
    private static void seedMaster(ServerPlayer player, Object store, Object quest,
                                   ResourceLocation qId, Registrar<ManasSkill> reg) throws Exception {
        @SuppressWarnings("unchecked")
        List<ResourceLocation> types = (List<ResourceLocation>) QD_TYPES.get(quest);
        for (ResourceLocation skillId : types) {
            ManasSkill skill = reg.get(skillId);
            if (skill != null) {
                ManasSkillInstance instance = SkillAPI.getSkillsFrom(player).getSkill(skill).orElse(null);
                if (instance != null && instance.isMastered(player)) {
                    STORE_MARK_COMPLETED.invoke(store, qId);
                    return;
                }
            }
        }
    }

    /** MASTER_TYPE 任务：统计已精通的对应类型技能数。 */
    private static void seedMasterType(ServerPlayer player, Object store, Object quest,
                                       ResourceLocation qId, Registrar<ManasSkill> reg,
                                       Skills skills) throws Exception {
        int amount = QD_AMOUNT.getInt(quest);
        if (amount <= 0) return;

        @SuppressWarnings("unchecked")
        List<ResourceLocation> types = (List<ResourceLocation>) QD_TYPES.get(quest);
        int count = 0;

        for (ManasSkillInstance instance : skills.getLearnedSkills()) {
            if (!instance.isMastered(player)) continue;
            ManasSkill ms = instance.getSkill();
            ResourceLocation msId = reg.getId(ms);
            if (msId == null) continue;
            if ((boolean) STORE_HAS_COUNTED.invoke(store, qId, msId.toString())) continue;

            ResourceLocation typeId = null;
            if (ms instanceof Skill s) {
                typeId = (ResourceLocation) SKILL_TYPE_ID.invoke(null, s.getType());
            } else if (ms instanceof Battlewill) {
                typeId = (ResourceLocation) BATTLEWILL_ID.invoke(null);
            }
            if (typeId != null && types.contains(typeId)) {
                STORE_ADD_COUNTED.invoke(store, qId, msId.toString());
                count++;
            }
        }
        if (count > 0) {
            STORE_ADD_PROGRESS.invoke(store, qId, count, Math.max(1, amount));
        }
    }

    /** MASTER_TYPE_MAGIC 任务：统计已精通的对应魔法类型数。 */
    private static void seedMasterTypeMagic(ServerPlayer player, Object store, Object quest,
                                            ResourceLocation qId, Registrar<ManasSkill> reg,
                                            Skills skills) throws Exception {
        int amount = QD_AMOUNT.getInt(quest);
        if (amount <= 0) return;

        @SuppressWarnings("unchecked")
        List<ResourceLocation> types = (List<ResourceLocation>) QD_TYPES.get(quest);
        int count = 0;

        for (ManasSkillInstance instance : skills.getLearnedSkills()) {
            if (!instance.isMastered(player)) continue;
            ManasSkill ms = instance.getSkill();
            if (!(ms instanceof Magic magic)) continue;
            ResourceLocation msId = reg.getId(ms);
            if (msId == null) continue;
            if ((boolean) STORE_HAS_COUNTED.invoke(store, qId, msId.toString())) continue;

            ResourceLocation magicTypeId = (ResourceLocation) MAGIC_TYPE_ID.invoke(null, magic.getType());
            if (types.contains(magicTypeId)) {
                STORE_ADD_COUNTED.invoke(store, qId, msId.toString());
                count++;
            }
        }
        if (count > 0) {
            STORE_ADD_PROGRESS.invoke(store, qId, count, Math.max(1, amount));
        }
    }

    /** MASTER_SUBTYPE_MAGIC 任务：统计已精通的对应魔法子类型数。 */
    private static void seedMasterSubtypeMagic(ServerPlayer player, Object store, Object quest,
                                               ResourceLocation qId, Registrar<ManasSkill> reg,
                                               Skills skills) throws Exception {
        int amount = QD_AMOUNT.getInt(quest);
        if (amount <= 0) return;

        @SuppressWarnings("unchecked")
        List<ResourceLocation> types = (List<ResourceLocation>) QD_TYPES.get(quest);
        int count = 0;

        for (ManasSkillInstance instance : skills.getLearnedSkills()) {
            if (!instance.isMastered(player)) continue;
            ManasSkill ms = instance.getSkill();
            if (!(ms instanceof Magic magic)) continue;
            ResourceLocation msId = reg.getId(ms);
            if (msId == null) continue;
            if ((boolean) STORE_HAS_COUNTED.invoke(store, qId, msId.toString())) continue;

            @SuppressWarnings("unchecked")
            Set<ResourceLocation> subtypeIds = (Set<ResourceLocation>) MAGIC_SUBTYPE_IDS.invoke(null, magic);
            boolean match = false;
            for (ResourceLocation subtypeId : subtypeIds) {
                if (types.contains(subtypeId)) {
                    match = true;
                    break;
                }
            }
            if (match) {
                STORE_ADD_COUNTED.invoke(store, qId, msId.toString());
                count++;
            }
        }
        if (count > 0) {
            STORE_ADD_PROGRESS.invoke(store, qId, count, Math.max(1, amount));
        }
    }

    private MasterySeedHelper() {}
}
