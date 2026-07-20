package com.tensura_tno.ability.skill;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.network.ContractLittleFoxPackets;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 契约小狐 —— 内在技能。按下技能键打开召唤/收回面板。
 *
 * <p>核心规则（参见 {@link ContractLittleFoxPackets} 的服务端处理）：
 * <ul>
 *   <li>同一玩家同时只能存在 1 只契约小狐灵；</li>
 *   <li>召唤不消耗魔素；可随时"收回"，被收回的小狐灵保留属性；</li>
 *   <li>小狐灵死亡时，下次召唤会"重置"为玩家当前 EP / MP / HP / SHP 的 50%；</li>
 *   <li>小狐灵击杀单位获得的 EP 50% 给玩家，剩余 50% 自留；</li>
 *   <li>玩家死亡时小狐灵立即消失，存档清空。</li>
 * </ul>
 */
public class ContractLittleFoxSkill extends Skill {

    public ContractLittleFoxSkill() {
        super(Skill.SkillType.INTRINSIC);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(
                TensuraTNOMod.MOD_ID, "textures/skill/contract_little_fox.png");
    }

    @Override
    public MutableComponent getColoredName() {
        MutableComponent name = super.getName();
        // 浅金色—— 与"契约 / 召唤"的语义匹配
        return name == null ? null : name.withColor(0xFFD27F);
    }

    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return 0.0;
    }

    /** 内在技能基础精通由 Skill 基类按 SkillType 配置，这里保持默认即可。 */

    /** 按下技能键 —— 仅在服务端打开 GUI 面板（无消耗、无冷却）。 */
    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;
        ContractLittleFoxPackets.openPanel(player);
    }
}
