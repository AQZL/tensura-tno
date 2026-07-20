package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.ability.skill.ContractLittleFoxSkill;
import com.tensura_tno.ability.skill.EdoTenseiSkill;
import com.tensura_tno.ability.skill.HumanFormSkill;
import com.tensura_tno.ability.skill.MagicOreSenseSkill;
import com.tensura_tno.ability.skill.SpiritEnhancementSkill;
import com.tensura_tno.ability.skill.SpiritSummonSkill;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 技能注册表。使用 DeferredRegister 向 ManasCore 技能注册表注册所有技能。
 * <p>
 * 添加新技能的方法：
 * <pre>
 *   public static final DeferredHolder&lt;ManasSkill, MySkill&gt; MY_SKILL =
 *       SKILL_REGISTRY.register("my_skill", MySkill::new);
 * </pre>
 */
public final class TensuraTNOSkills {

    public static final DeferredRegister<ManasSkill> SKILL_REGISTRY =
            DeferredRegister.create(SkillAPI.getSkillRegistryKey(), TensuraTNOMod.MOD_ID);

    // ====================
    // | Intrinsic Skills |
    // ====================

    /** 契约小狐 —— 召唤一只协助战斗的小狐灵（详见 {@link ContractLittleFoxSkill}）。 */
    public static final DeferredHolder<ManasSkill, ContractLittleFoxSkill> CONTRACT_LITTLE_FOX =
            SKILL_REGISTRY.register("contract_little_fox", ContractLittleFoxSkill::new);

    /** 灵之召唤 —— 收纳半血以下生物并召唤出来（详见 {@link SpiritSummonSkill}）。 */
    public static final DeferredHolder<ManasSkill, SpiritSummonSkill> SPIRIT_SUMMON =
            SKILL_REGISTRY.register("spirit_summon", SpiritSummonSkill::new);

    // ================
    // | Extra Skills |
    // ================

    public static final DeferredHolder<ManasSkill, EdoTenseiSkill> EDO_TENSEI =
            SKILL_REGISTRY.register("edo_tensei", EdoTenseiSkill::new);

    public static final DeferredHolder<ManasSkill, SpiritEnhancementSkill> SPIRIT_ENHANCEMENT =
            SKILL_REGISTRY.register("spirit_enhancement", SpiritEnhancementSkill::new);

    public static final DeferredHolder<ManasSkill, MagicOreSenseSkill> MAGIC_ORE_SENSE =
            SKILL_REGISTRY.register("magic_ore_sense", MagicOreSenseSkill::new);

    /**
     * 人形状态 —— 真魔王或真勇者解锁的内在被动 toggle 技能，强制将体型锁成史蒂夫 1.0F。
     * 详见 {@link HumanFormSkill}。
     */
    public static final DeferredHolder<ManasSkill, HumanFormSkill> HUMAN_FORM =
            SKILL_REGISTRY.register("human_form", HumanFormSkill::new);

    // =================
    // | Unique Skills |
    // =================

    // =====================
    // | Ultimate Skills   |
    // =====================
    // 智慧之王·拉斐尔 已迁移至独立子模组 foxablazeultimate

    private TensuraTNOSkills() {
    }

    public static void register(IEventBus modBus) {
        SKILL_REGISTRY.register(modBus);
    }
}
