package com.tensura_tno.ability.skill;

import com.tensura_tno.TensuraTNOMod;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 人形状态（Human Form）—— 内在主动技能。
 *
 * <p>解锁条件：玩家首次成为「真魔王(True Demon Lord)」或「真勇者(True Hero)」之一。
 * 学会后永久保留，与玩家此后是否仍持有真身份无关。
 *
 * <p>效果：在玩家 {@link Attributes#SCALE} 上注入一个唯一 key
 * {@code tensura_tno:human_form_scale} 的 ADD_VALUE 修饰器，
 * 修饰值 = {@code 1.0 - 当前 SCALE 最终值（不含本修饰器）}，从而强制最终 SCALE = 1.0F，
 * 无视所有其他种族 / 技能带来的体型变化。
 *
 * <p>触发：仅按键一次（{@link #onPressed}），消耗 {@value #MAGICULE_COST} 魔素。
 * 不提供被动 toggle —— {@link #canBeToggled} 永远返回 false。
 *
 * <p>种族变化清理：当玩家通过重置卷等方式切换种族时，残留的修饰器会与新种族的体型属性叠加，
 * 导致新种族矮一截。{@link com.tensura_tno.event.HumanFormRaceChangeHandler} 监听
 * {@code RaceEvents.SET_RACE} 在切换瞬间移除本技能的 SCALE 修饰器。
 */
public class HumanFormSkill extends Skill {

    /** 修饰器唯一 key —— 全模组用这一个，便于 add/remove 都精准命中。 */
    public static final ResourceLocation HUMAN_FORM_SCALE =
            ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "human_form_scale");

    /**
     * 移动速度补偿修饰器 key。Slime 种族 base 阶段 movementSpeed = -0.03（慢于人类种族的 0.0）；
     * 变成人形后玩家应感受到"人类速度"，因此用一个 +0.03 的 ADD_VALUE 修饰器抵消。
     * 修饰器与 SCALE 同生命周期：{@link #applyHumanForm} 注入，{@link #removeHumanForm} 移除。
     */
    public static final ResourceLocation HUMAN_FORM_SPEED =
            ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "human_form_speed");

    /** 抵消 slime base 阶段 movementSpeed = -0.03 的速度补偿值。 */
    private static final double SPEED_COMPENSATION = 0.03D;

    /**
     * Tensura 在 LivingEntity#getDimensions 里把 vanilla 尺寸再乘以这两个属性。
     * 蜘蛛子种族 (ArachneRace 等) 通过这两个属性把碰撞箱放大，仅锁 SCALE 不够，
     * 必须把这两个属性的最终值也强制成 1.0 才能让碰撞箱回到史蒂夫大小。
     * 用 ResourceLocation 查表，避免硬依赖 Tensura 主模组的 TensuraAttributes class。
     */
    private static final ResourceLocation TENSURA_WIDTH_MULTIPLIER_ID =
            ResourceLocation.fromNamespaceAndPath("tensura", "width_multiplier");
    private static final ResourceLocation TENSURA_HEIGHT_MULTIPLIER_ID =
            ResourceLocation.fromNamespaceAndPath("tensura", "height_multiplier");

    /** 强制锁定的最终目标值（原版史蒂夫体型的 1.0 倍）。 */
    private static final double TARGET_VALUE = 1.0D;

    /** 每次按键消耗的魔素量。 */
    public static final double MAGICULE_COST = 1000.0D;

    public HumanFormSkill() {
        super(Skill.SkillType.INTRINSIC);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(
                TensuraTNOMod.MOD_ID, "textures/skill/human_form.png");
    }

    /**
     * 解锁条件：成为真魔王或真勇者之一。
     * <p>注：学会后是否保留由 ManasCore 技能存储自动负责，本类无需做"学过就别忘"的额外判断。
     */
    @Override
    public boolean checkAcquiringRequirement(Player entity, double newEP) {
        IExistence existence = TensuraStorages.getExistenceFrom(entity);
        return existence.isTrueDemonLord() || existence.isTrueHero();
    }

    /** 禁用被动勾选。 */
    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) {
        return false;
    }

    /** 不参与每 tick 回调（性能约束）。 */
    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return false;
    }

    /** 每按一次消耗 1000 魔素。 */
    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return MAGICULE_COST;
    }

    /**
     * 按键触发：扣魔素 → 注入修饰器把最终 SCALE 锁成 1.0。
     * 魔素不足时 {@link EnergyHelper#isOutOfEnergy} 会返回 true 并在客户端提示，本方法直接返回。
     */
    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity.level().isClientSide()) return;
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
        applyHumanForm(entity);
    }

    // ============================================================
    // | 静态工具方法
    // ============================================================

    /**
     * 注入"人形状态"修饰器，使 {@link Attributes#SCALE}、Tensura
     * {@code width_multiplier} 与 {@code height_multiplier} 三个属性的最终值
     * 都等于 {@value #TARGET_VALUE}，并刷新实体碰撞箱。
     * <p>实现细节：先移除自身已有修饰器，避免计算 delta 时把自己算进去；
     * 再以 {@code 1.0 - currentFinal} 作为 ADD_VALUE 注入。
     */
    public static void applyHumanForm(LivingEntity entity) {
        boolean changed = lockToOne(entity, entity.getAttribute(Attributes.SCALE));
        changed |= lockToOne(entity, getAttribute(entity, TENSURA_WIDTH_MULTIPLIER_ID));
        changed |= lockToOne(entity, getAttribute(entity, TENSURA_HEIGHT_MULTIPLIER_ID));
        applySpeedCompensation(entity);
        if (changed) {
            entity.refreshDimensions();
        }
    }

    /** 仅移除 {@code tensura_tno:human_form_scale}，不动其他修饰器，并刷新碰撞箱。 */
    public static void removeHumanForm(LivingEntity entity) {
        boolean removed = removeOurModifier(entity.getAttribute(Attributes.SCALE));
        removed |= removeOurModifier(getAttribute(entity, TENSURA_WIDTH_MULTIPLIER_ID));
        removed |= removeOurModifier(getAttribute(entity, TENSURA_HEIGHT_MULTIPLIER_ID));
        removeSpeedCompensation(entity);
        if (removed) {
            entity.refreshDimensions();
        }
    }

    /**
     * 注入移动速度补偿，使 slime 种族在人形状态下移动速度与 human 种族对齐。
     * 用 {@link AttributeModifier.Operation#ADD_VALUE} 直接加 0.03 到 base，
     * 与 tensura 种族 modifier 同 operation，叠加结果可预测。
     */
    private static void applySpeedCompensation(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(HUMAN_FORM_SPEED);
        speed.addOrReplacePermanentModifier(
                new AttributeModifier(HUMAN_FORM_SPEED, SPEED_COMPENSATION,
                        AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeSpeedCompensation(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (speed.getModifier(HUMAN_FORM_SPEED) == null) return;
        speed.removeModifier(HUMAN_FORM_SPEED);
    }

    private static boolean lockToOne(LivingEntity entity, AttributeInstance instance) {
        if (instance == null) return false;
        instance.removeModifier(HUMAN_FORM_SCALE);
        double delta = TARGET_VALUE - instance.getValue();
        instance.addOrReplacePermanentModifier(
                new AttributeModifier(HUMAN_FORM_SCALE, delta, AttributeModifier.Operation.ADD_VALUE));
        return true;
    }

    private static boolean removeOurModifier(AttributeInstance instance) {
        if (instance == null) return false;
        if (instance.getModifier(HUMAN_FORM_SCALE) == null) return false;
        instance.removeModifier(HUMAN_FORM_SCALE);
        return true;
    }

    private static AttributeInstance getAttribute(LivingEntity entity, ResourceLocation id) {
        Attribute raw = BuiltInRegistries.ATTRIBUTE.get(id);
        if (raw == null) return null;
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(raw);
        return entity.getAttribute(holder);
    }
}
