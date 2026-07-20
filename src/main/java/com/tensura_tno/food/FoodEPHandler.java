package com.tensura_tno.food;

import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Food EP system:
 * - Only food stamped by a cook-skilled player grants EP/MP/AP.
 * - Stamping triggers:
 *     1. When a cook crafts / smelts food (immediate)
 *     2. When the eater starts eating (covers every acquisition path)
 *     3. When a cook closes any container (covers chest/trade)
 *     4. When a cook logs in (covers existing items)
 * - Already-stamped food is never re-stamped.
 * - Any player can eat stamped food and gain its benefits.
 */
public final class FoodEPHandler {

    private FoodEPHandler() {}

    private static final ConcurrentHashMap<UUID, Long> LAST_EAT_TICK = new ConcurrentHashMap<>();

    // ── Stamp on craft / smelt ────────────────────────────────────────────────

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        CookStamp.tryStamp(event.getCrafting(), event.getEntity());
    }

    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        CookStamp.tryStamp(event.getSmelting(), event.getEntity());
    }

    // ── Stamp when a cook-player STARTS eating (covers every item source) ─────

    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ItemStack stack = event.getItem();
        if (stack.getFoodProperties(player) == null) return;
        CookStamp.tryStamp(stack, player);
    }

    // ── Food consumption — only stamped food grants EP ────────────────────────

    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        ItemStack stack = event.getItem();
        if (stack.getFoodProperties(event.getEntity()) == null) return;

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        long now = entity.level().getGameTime();
        Long prev = LAST_EAT_TICK.put(entity.getUUID(), now);
        if (prev != null && prev == now) return;

        if (!CookStamp.isStamped(stack)) return;

        double bonus = CookStamp.getBonus(stack);
        double effectScale = CookStamp.getEffectScale(stack);

        double ep = FoodEPManager.resolveEP(stack, entity);
        if (ep > 0) {
            addMaxEPCorrectly(entity, ep * bonus * effectScale);
        }

        double mp = FoodEPManager.resolveMP(stack, entity);
        if (mp > 0) {
            EnergyHelper.gainMagicule(entity, mp * effectScale, EnergyHelper.GainType.NORMAL);
        }

        double ap = FoodEPManager.resolveAP(stack, entity);
        if (ap > 0) {
            EnergyHelper.gainAura(entity, ap * effectScale, EnergyHelper.GainType.NORMAL);
        }
    }

    private static void addMaxEPCorrectly(LivingEntity entity, double totalEP) {
        double half = totalEP / 2.0;
        AttributeInstance aura = entity.getAttribute(TensuraAttributes.MAX_AURA);
        if (aura != null) aura.setBaseValue(aura.getBaseValue() + half);
        AttributeInstance magicule = entity.getAttribute(TensuraAttributes.MAX_MAGICULE);
        if (magicule != null) magicule.setBaseValue(magicule.getBaseValue() + half);
    }

    // ── Tooltip ──────────────────────────────────────────────────────────────

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getFoodProperties(null) == null) return;
        if (!CookStamp.isStamped(stack)) return;

        List<Component> tooltip = event.getToolTip();

        double bonus = CookStamp.getBonus(stack);
        double effectScale = CookStamp.getEffectScale(stack);
        int bonusPct = (int) Math.round((bonus - 1.0) * 100);
        int scalePct = (int) Math.round(effectScale * 100);

        String epStr = FoodEPManager.getTooltipEP(stack);
        if (epStr != null && !isZero(epStr)) {
            Component epLine = Component.translatable("tensura_tno.food.tooltip.ep", epStr)
                    .withStyle(ChatFormatting.DARK_AQUA);
            if (bonusPct != 0) {
                String sign = bonusPct > 0 ? "+" : "";
                epLine = epLine.copy().append(
                        Component.literal(" (" + sign + bonusPct + "%)")
                                .withStyle(bonusPct > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            if (scalePct != 100) {
                epLine = epLine.copy().append(
                        Component.literal(" (" + scalePct + "%)")
                                .withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(epLine);
        }

        String mpStr = FoodEPManager.getTooltipMP(stack);
        if (mpStr != null && !isZero(mpStr)) {
            tooltip.add(Component.translatable("tensura_tno.food.tooltip.mp", mpStr)
                    .withStyle(ChatFormatting.BLUE));
        }

        String apStr = FoodEPManager.getTooltipAP(stack);
        if (apStr != null && !isZero(apStr)) {
            tooltip.add(Component.translatable("tensura_tno.food.tooltip.ap", apStr)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean isZero(String value) {
        try {
            String v = value.trim();
            if (v.endsWith("%")) v = v.substring(0, v.length() - 1);
            return Double.parseDouble(v) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
