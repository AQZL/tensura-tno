package com.tensura_tno.food;

import java.util.concurrent.ThreadLocalRandom;

import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Stamps food items with EP bonus data when produced by a player who has the
 * {@code tensura:cook} unique skill.
 *
 * NBT layout on the ItemStack's tag:
 *   tno_cook_stamp: {
 *     bonus: <double>   // multiplier applied to base EP, e.g. 0.92 or 1.015
 *     stamped: true
 *   }
 *
 * Mastery-based bonus ranges:
 *   mastery < 10:        -15% to -6%   → multiplier 0.85 – 0.94
 *   10 ≤ mastery < 50:   -5%  to -2%   → multiplier 0.95 – 0.98
 *   50 ≤ mastery < 100:  0%            → multiplier 1.00
 *   mastery = 100:       +1%  to +2%   → multiplier 1.01 – 1.02
 */
public final class CookStamp {

    private static final String TAG_KEY = "tno_cook_stamp";
    private static final String TAG_BONUS = "bonus";
    private static final String TAG_STAMPED = "stamped";
    private static final String TAG_EFFECT_SCALE = "effect_scale";
    private static final ResourceLocation COOK_SKILL_ID =
            ResourceLocation.fromNamespaceAndPath("tensura", "cook");

    private CookStamp() {}

    private static CompoundTag getCustomNbt(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    private static void setCustomNbt(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * 移除一份戳记。若移除后 CustomData 没有其他字段，则连同 CUSTOM_DATA 组件一起清掉，
     * 这样物品恢复"无组件"状态，可与未戳记的同类物品堆叠。
     */
    private static void stripStamp(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_KEY)) return;
        tag.remove(TAG_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /** Returns true if this stack already has a cook stamp (cannot be re-stamped). */
    public static boolean isStamped(ItemStack stack) {
        CompoundTag root = getCustomNbt(stack);
        return root.contains(TAG_KEY) && root.getCompound(TAG_KEY).getBoolean(TAG_STAMPED);
    }

    /** Returns the EP multiplier stored on this stack, or 0 if not stamped. */
    public static double getBonus(ItemStack stack) {
        CompoundTag root = getCustomNbt(stack);
        if (!root.contains(TAG_KEY)) return 0;
        CompoundTag stamp = root.getCompound(TAG_KEY);
        if (!stamp.getBoolean(TAG_STAMPED)) return 0;
        return stamp.getDouble(TAG_BONUS);
    }

    public static double getEffectScale(ItemStack stack) {
        CompoundTag root = getCustomNbt(stack);
        if (!root.contains(TAG_KEY)) return 0;
        CompoundTag stamp = root.getCompound(TAG_KEY);
        if (!stamp.getBoolean(TAG_STAMPED)) return 0;
        return stamp.contains(TAG_EFFECT_SCALE) ? stamp.getDouble(TAG_EFFECT_SCALE) : 1.0;
    }

    /**
     * Attempts to stamp a food item if the player has the cook skill.
     * Does nothing if the item is not food, already stamped, or player has no cook skill.
     * <p>
     * "Edible" items with zero nutrition AND zero saturation are intentionally skipped
     * (e.g. {@code tensura:dragon_essence}, {@code daemon_essence}, blood items …)：
     * 这些物品技术上是 alwaysEdible() 食物，但没有饱食度，本身只是右键即用的功能性物品；
     * 给它们打 NBT 戳会导致同种物品因 NBT 不一致而无法堆叠。
     */
    public static void tryStamp(ItemStack stack, Player player) {
        if (stack.isEmpty()) return;
        var food = stack.getFoodProperties(player);
        if (food == null) return;
        // 跳过无饱食度的"伪食物"，避免在不可堆叠的非饱食物品上加 NBT。
        // 如果旧版本错误地戳过这种物品，这里顺手把 NBT 剥掉，让它重新可堆叠。
        if (food.nutrition() <= 0 && food.saturation() <= 0.0F) {
            stripStamp(stack);
            return;
        }
        if (normalizeStamp(stack)) return;

        double mastery = getCookMastery(player);
        if (mastery < 0) return;

        double multiplier = rollMultiplier(mastery);

        stamp(stack, multiplier, 1.0);
    }

    public static void tryStampScaled(ItemStack stack, LivingEntity entity, double effectScale) {
        if (stack.isEmpty()) return;
        var food = stack.getFoodProperties(entity);
        if (food == null) return;
        if (food.nutrition() <= 0 && food.saturation() <= 0.0F) {
            stripStamp(stack);
            return;
        }
        if (normalizeStamp(stack)) return;

        double multiplier = 1.0;
        if (entity instanceof Player player) {
            double mastery = getCookMastery(player);
            if (mastery >= 0) {
                multiplier = rollMultiplier(mastery);
            }
        }

        stamp(stack, multiplier, effectScale);
    }

    /**
     * 魔素灶台专用：在 {@link #tryStampScaled} 的精通度基础 multiplier 之上，
     * 若玩家拥有 Cook 独有技能，则额外叠加一个 {@code +1% ~ +5%} 的随机 bonus。
     * 不持有 Cook 时与 {@link #tryStampScaled} 行为一致（multiplier = 1.0）。
     */
    public static void tryStampScaledWithStoveBonus(ItemStack stack, LivingEntity entity, double effectScale) {
        if (stack.isEmpty()) return;
        var food = stack.getFoodProperties(entity);
        if (food == null) return;
        if (food.nutrition() <= 0 && food.saturation() <= 0.0F) {
            stripStamp(stack);
            return;
        }
        if (normalizeStamp(stack)) return;

        double multiplier = 1.0;
        if (entity instanceof Player player) {
            double mastery = getCookMastery(player);
            if (mastery >= 0) {
                multiplier = rollMultiplier(mastery);
                // 拥有 Cook + 在魔素灶台上做菜：额外随机 +1% ~ +5%
                multiplier += ThreadLocalRandom.current().nextDouble(0.01, 0.0501);
            }
        }

        stamp(stack, multiplier, effectScale);
    }

    private static void stamp(ItemStack stack, double multiplier, double effectScale) {
        CompoundTag root = getCustomNbt(stack);
        CompoundTag stamp = new CompoundTag();
        stamp.putBoolean(TAG_STAMPED, true);
        stamp.putDouble(TAG_BONUS, normalizeMultiplier(multiplier));
        stamp.putDouble(TAG_EFFECT_SCALE, effectScale);
        root.put(TAG_KEY, stamp);
        setCustomNbt(stack, root);
    }

    /**
     * 物品能否堆叠取决于完整组件数据是否相同。Tooltip 只显示整数百分比，
     * 因此这里把真实 multiplier 也归一到同一档位，避免两个都显示 -8% 的食物
     * 因为隐藏小数不同而无法堆叠。
     */
    private static double normalizeMultiplier(double multiplier) {
        int bonusPct = (int) Math.round((multiplier - 1.0) * 100.0);
        return 1.0 + bonusPct / 100.0;
    }

    /**
     * 兼容旧存档里已经写入连续随机小数的食物。只要物品再次进入玩家物品栏、
     * 开始食用或被灶台产出流程触碰，就会把旧 bonus 修正到显示百分比对应的值。
     */
    private static boolean normalizeStamp(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;

        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_KEY)) return false;

        CompoundTag stamp = root.getCompound(TAG_KEY);
        if (!stamp.getBoolean(TAG_STAMPED)) return false;

        if (stamp.contains(TAG_BONUS)) {
            double oldBonus = stamp.getDouble(TAG_BONUS);
            double normalizedBonus = normalizeMultiplier(oldBonus);
            if (Double.compare(oldBonus, normalizedBonus) != 0) {
                stamp.putDouble(TAG_BONUS, normalizedBonus);
                root.put(TAG_KEY, stamp);
                setCustomNbt(stack, root);
            }
        }

        return true;
    }

    /**
     * Returns the cook skill mastery (0–100) or -1 if the player doesn't have the skill.
     */
    public static double getCookMastery(Player player) {
        Skills skills = SkillAPI.getSkillsFrom(player);
        for (var inst : skills.getLearnedSkills()) {
            ResourceLocation id = inst.getSkillId();
            if (COOK_SKILL_ID.equals(id)) {
                return inst.getMastery();
            }
        }
        return -1;
    }

    private static double rollMultiplier(double mastery) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (mastery >= 100) {
            return 1.0 + rng.nextDouble(0.01, 0.021);
        } else if (mastery >= 50) {
            return 1.0;
        } else if (mastery >= 10) {
            return 1.0 + rng.nextDouble(-0.05, -0.019);
        } else {
            return 1.0 + rng.nextDouble(-0.15, -0.059);
        }
    }
}
