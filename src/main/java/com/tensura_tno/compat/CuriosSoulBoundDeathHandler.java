package com.tensura_tno.compat;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 修复：带有灵魂绑定附魔的饰品放在由其他饰品动态提供的额外护符栏中，
 * 玩家死亡时物品消失而非进入墓碑。
 *
 * 根本原因（Curios shrink 问题）：
 *   YIGD 在 PlayerEvent.Clone LOWEST 阶段将灵魂绑定物品放回 Curios 的额外槽位。
 *   随后 Curios 的 EntityTickEvent 检测到提供该额外槽位的饰品已不在身上（在墓碑里），
 *   因此调用 DynamicStackHandler.shrink()，直接截断列表并静默销毁末位槽的物品。
 *
 * 修复策略：
 *   1. LivingDeathEvent HIGH（YIGD LOWEST 之前）：将额外槽位中的灵魂绑定物品提取出来，
 *      保存到玩家 PersistentData（SAVE_KEY），并清空对应槽位。
 *      YIGD 快照时看到空槽 → 不会将物品加入 DropRule.KEEP → 不会在复活时放回额外槽。
 *   2. LivingDropsEvent HIGH（YIGD LOWEST 之前）：从 SAVE_KEY 读取物品，
 *      通过 DeathHandler.addItem() 注入 YIGD 墓碑的 vanilla 物品区，物品安全进入墓碑。
 *      注入成功后清除 SAVE_KEY。
 *
 * 边缘情况兜底：
 *   - 死亡被取消（NORMAL 优先级事件在 HIGH 之后撤销死亡）：LivingDropsEvent 不会触发，
 *     SAVE_KEY 残留。玩家仍然存活且额外槽位仍然存在，PlayerTickEvent 在短暂延迟后
 *     将物品恢复到原槽位。
 *   - 注入失败（反射异常等）：SAVE_KEY 保留，PlayerEvent.Clone 将其转移到新玩家，
 *     PlayerTickEvent 在超时后将物品放入背包，不会丢失。
 */
public class CuriosSoulBoundDeathHandler {

    /** PersistentData 键，存储待处理的灵魂绑定额外槽位物品 */
    private static final String SAVE_KEY = "tensura_tno:curios_soulbound_extra";

    /** 死亡被取消后等待多少 tick 再尝试恢复（让 Curios 完成当前 tick 处理） */
    private static final int RESTORE_DELAY = 2;

    /** 超过此 tick 数仍无法恢复则放入背包 */
    private static final int RESTORE_TIMEOUT = 60;

    /** YIGD 的灵魂绑定附魔 tag（包含 yigd:soulbound 及各兼容灵魂绑定附魔） */
    private static final TagKey<Enchantment> YIGD_SOULBOUND_TAG =
            TagKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath("yigd", "soulbound"));

    public static void register() {
        if (!ModList.get().isLoaded("curios")) {
            TensuraTNOMod.LOGGER.info("[TensuraTNO] Curios 未加载，跳过额外槽位灵魂绑定死亡修复。");
            return;
        }
        if (!ModList.get().isLoaded("yigd")) {
            TensuraTNOMod.LOGGER.info("[TensuraTNO] YIGD 未加载，跳过额外槽位灵魂绑定死亡修复。");
            return;
        }

        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingDeathEvent.class,
                CuriosSoulBoundDeathHandler::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingDropsEvent.class,
                CuriosSoulBoundDeathHandler::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(
                CuriosSoulBoundDeathHandler::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(
                CuriosSoulBoundDeathHandler::onPlayerTick);

        TensuraTNOMod.LOGGER.info("[TensuraTNO] 已注册 Curios 额外槽位灵魂绑定死亡修复（YIGD 模式）。");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 主流程事件监听器
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LivingDeathEvent HIGH：在 YIGD 的 LOWEST 之前运行。
     *
     * 扫描所有 Curios 槽位，找到额外槽位（index >= 该槽位类型的基础配置数量）中
     * 带有灵魂绑定附魔的物品，将其保存到 SAVE_KEY 并清空对应槽位。
     * YIGD 随后快照到空槽，不会将这些物品标记为 DropRule.KEEP。
     */
    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        try {
            ListTag saved = extractSoulboundFromExtraSlots(player);
            if (!saved.isEmpty()) {
                player.getPersistentData().put(SAVE_KEY, saved);
                TensuraTNOMod.LOGGER.debug(
                        "[TensuraTNO] 死亡提取：从额外槽位取出 {} 个灵魂绑定物品（玩家: {}）",
                        saved.size(), player.getName().getString());
            }
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.error("[TensuraTNO] onLivingDeath 出错", e);
        }
    }

    /**
     * LivingDropsEvent HIGH：在 YIGD 的 LOWEST 之前运行。
     *
     * 读取 SAVE_KEY 中保存的物品，通过反射调用 DeathHandler.addItem() 将它们注入
     * YIGD 的墓碑（vanilla 物品区），玩家可从墓碑中取回。
     * 注入成功后清除 SAVE_KEY；失败则保留 SAVE_KEY 交给边缘情况处理器。
     */
    private static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;
        if (!player.getPersistentData().contains(SAVE_KEY, Tag.TAG_LIST)) return;

        ListTag saved = player.getPersistentData().getList(SAVE_KEY, Tag.TAG_COMPOUND);
        if (saved.isEmpty()) {
            player.getPersistentData().remove(SAVE_KEY);
            return;
        }

        try {
            Class<?> yigdClass = Class.forName("com.b1n_ry.yigd.Yigd");
            Field field = yigdClass.getField("UNFINISHED_DEATHS");
            @SuppressWarnings("unchecked")
            Map<UUID, Object> unfinishedDeaths = (Map<UUID, Object>) field.get(null);
            Object deathHandler = unfinishedDeaths.get(player.getUUID());

            if (deathHandler == null) {
                // YIGD 没有为此次死亡创建 DeathHandler（死亡被取消或被其他逻辑处理）。
                // 保留 SAVE_KEY，由 PlayerTickEvent 将物品恢复到原槽位。
                TensuraTNOMod.LOGGER.debug(
                        "[TensuraTNO] 未找到 YIGD DeathHandler，物品保留待 tick 处理器恢复（玩家: {}）",
                        player.getName().getString());
                return;
            }

            // 逐一注入墓碑
            Method addItem = deathHandler.getClass().getMethod("addItem", ItemStack.class);
            int count = 0;
            for (int i = 0; i < saved.size(); i++) {
                ItemStack stack = ItemStack.parse(
                        player.registryAccess(), saved.getCompound(i).getCompound("item"))
                        .orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    addItem.invoke(deathHandler, stack);
                    count++;
                }
            }

            player.getPersistentData().remove(SAVE_KEY);
            TensuraTNOMod.LOGGER.debug(
                    "[TensuraTNO] 已将 {} 个灵魂绑定物品注入 YIGD 墓碑（玩家: {}）",
                    count, player.getName().getString());

        } catch (Exception e) {
            // 注入失败：保留 SAVE_KEY，PlayerEvent.Clone 会转移到新玩家，
            // PlayerTickEvent 最终将物品放入背包，不会丢失。
            TensuraTNOMod.LOGGER.error(
                    "[TensuraTNO] onLivingDrops 注入失败，物品将由 tick 处理器兜底（玩家: {}）",
                    player.getName().getString(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 边缘情况兜底
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PlayerEvent.Clone（复活时）：将 SAVE_KEY 从旧玩家复制到新玩家。
     *
     * 正常流程中 SAVE_KEY 已在 onLivingDrops 中消费。
     * 若注入失败（异常）导致 SAVE_KEY 残留，在此将其转移到新玩家，
     * 由新玩家的 PlayerTickEvent 处理器兜底放入背包。
     */
    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();

        if (!oldPlayer.getPersistentData().contains(SAVE_KEY, Tag.TAG_LIST)) return;

        ListTag saved = oldPlayer.getPersistentData().getList(SAVE_KEY, Tag.TAG_COMPOUND);
        oldPlayer.getPersistentData().remove(SAVE_KEY);
        if (!saved.isEmpty()) {
            newPlayer.getPersistentData().put(SAVE_KEY, saved.copy());
            TensuraTNOMod.LOGGER.debug(
                    "[TensuraTNO] Clone：转移 {} 个未处理物品到新玩家（玩家: {}）",
                    saved.size(), newPlayer.getName().getString());
        }
    }

    /**
     * PlayerTickEvent.Pre：兜底处理 SAVE_KEY 中残留的物品。
     *
     * 场景一（死亡被取消，玩家存活）：等 RESTORE_DELAY tick 后尝试恢复到原 Curios 槽位。
     *   此时皮带仍然装备，额外槽位存在，恢复通常立即成功。
     *
     * 场景二（注入失败，新玩家复活）：extra 槽位不存在（皮带在墓碑），
     *   恢复失败，超过 RESTORE_TIMEOUT 后将物品放入背包，不会永久丢失。
     */
    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) return;
        if (!player.getPersistentData().contains(SAVE_KEY, Tag.TAG_LIST)) return;

        ListTag saved = player.getPersistentData().getList(SAVE_KEY, Tag.TAG_COMPOUND);
        if (saved.isEmpty()) {
            player.getPersistentData().remove(SAVE_KEY);
            return;
        }

        ListTag remaining = new ListTag();
        List<ItemStack> giveToInventory = new ArrayList<>();

        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = saved.getCompound(i).copy();
            int ticks = entry.getInt("ticks") + 1;
            entry.putInt("ticks", ticks);

            if (ticks <= RESTORE_DELAY) {
                remaining.add(entry);
                continue;
            }

            if (tryRestoreToSlot(entry, player)) {
                // 成功恢复到原 Curios 槽位
            } else if (ticks <= RESTORE_DELAY + RESTORE_TIMEOUT) {
                remaining.add(entry);
            } else {
                ItemStack stack = ItemStack.parse(
                        player.registryAccess(), entry.getCompound("item"))
                        .orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) giveToInventory.add(stack);
            }
        }

        if (remaining.isEmpty()) {
            player.getPersistentData().remove(SAVE_KEY);
        } else {
            player.getPersistentData().put(SAVE_KEY, remaining);
        }

        for (ItemStack stack : giveToInventory) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        if (!giveToInventory.isEmpty()) {
            TensuraTNOMod.LOGGER.debug(
                    "[TensuraTNO] {} 个物品超时无法恢复，已放入背包/掉落（玩家: {}）",
                    giveToInventory.size(), player.getName().getString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 扫描玩家所有 Curios 槽位，提取额外槽位（index >= 该类型基础槽数量）中
     * 带有灵魂绑定附魔的物品，清空对应槽位，返回提取记录列表。
     *
     * 每条记录格式：{ "s": slotId, "i": slotIndex, "c": cosmetic, "ticks": 0, "item": ItemNBT }
     */
    private static ListTag extractSoulboundFromExtraSlots(ServerPlayer player) throws Exception {
        ListTag result = new ListTag();

        Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        Method getInv = api.getMethod("getCuriosInventory", LivingEntity.class);

        @SuppressWarnings("unchecked")
        Optional<Object> optHandler = (Optional<Object>) getInv.invoke(null, player);
        if (optHandler.isEmpty()) return result;

        @SuppressWarnings("unchecked")
        Map<String, Object> curios = (Map<String, Object>)
                optHandler.get().getClass().getMethod("getCurios").invoke(optHandler.get());

        for (Map.Entry<String, Object> entry : curios.entrySet()) {
            String slotId = entry.getKey();
            Object stacksHandler = entry.getValue();
            int baseCount = getBaseSlotCount(api, slotId, player);

            extractSoulboundFromHandler(player, slotId, stacksHandler, false, baseCount, result);
            try {
                extractSoulboundFromHandler(player, slotId, stacksHandler, true, baseCount, result);
            } catch (NoSuchMethodException ignored) {
                // 无装饰槽（cosmetic slots），跳过
            }
        }
        return result;
    }

    private static void extractSoulboundFromHandler(ServerPlayer player, String slotId,
            Object stacksHandler, boolean cosmetic, int baseCount, ListTag out) throws Exception {
        String methodName = cosmetic ? "getCosmeticStacks" : "getStacks";
        IItemHandlerModifiable stacks = (IItemHandlerModifiable)
                stacksHandler.getClass().getMethod(methodName).invoke(stacksHandler);

        for (int i = 0; i < stacks.getSlots(); i++) {
            if (i < baseCount) continue;              // 基础槽位，不处理
            ItemStack stack = stacks.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!isSoulbound(stack)) continue;        // 非灵魂绑定，不处理

            CompoundTag record = new CompoundTag();
            record.putString("s", slotId);
            record.putInt("i", i);
            record.putBoolean("c", cosmetic);
            record.putInt("ticks", 0);
            record.put("item", stack.save(player.registryAccess()));
            out.add(record);

            stacks.setStackInSlot(i, ItemStack.EMPTY); // 清空槽位，YIGD 快照时看到空槽
        }
    }

    /**
     * 获取指定槽位类型的基础（静态配置）槽数量。
     * 基础数量之外的槽位是由装备的饰品动态添加的"额外槽位"。
     * 获取失败时返回保守默认值 1。
     */
    private static int getBaseSlotCount(Class<?> api, String slotId, ServerPlayer player) {
        try {
            Method getSlot = api.getMethod("getSlot", String.class,
                    net.minecraft.world.level.Level.class);
            @SuppressWarnings("unchecked")
            Optional<Object> opt = (Optional<Object>) getSlot.invoke(null, slotId, player.level());
            if (opt.isPresent()) {
                return (int) opt.get().getClass().getMethod("getSize").invoke(opt.get());
            }
        } catch (Exception ignored) {}
        return 1;
    }

    /**
     * 将 record 记录的物品恢复到对应 Curios 槽位。
     * 槽位不存在（皮带未装备）或已被占用时返回 false。
     */
    private static boolean tryRestoreToSlot(CompoundTag record, Player player) {
        if (!(player instanceof ServerPlayer sp)) return false;
        String slotId = record.getString("s");
        int idx = record.getInt("i");
        boolean cosmetic = record.getBoolean("c");

        ItemStack stack = ItemStack.parse(player.registryAccess(), record.getCompound("item"))
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) return true; // 空物品视为成功

        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            @SuppressWarnings("unchecked")
            Optional<Object> optHandler = (Optional<Object>)
                    api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, sp);
            if (optHandler.isEmpty()) return false;

            @SuppressWarnings("unchecked")
            Map<String, Object> curios = (Map<String, Object>)
                    optHandler.get().getClass().getMethod("getCurios").invoke(optHandler.get());

            Object sh = curios.get(slotId);
            if (sh == null) return false;

            IItemHandlerModifiable stacks = (IItemHandlerModifiable)
                    sh.getClass().getMethod(cosmetic ? "getCosmeticStacks" : "getStacks").invoke(sh);

            if (idx >= stacks.getSlots()) return false;  // 槽位不存在（额外槽尚未被创建）
            if (!stacks.getStackInSlot(idx).isEmpty()) return false; // 槽位已有物品

            stacks.setStackInSlot(idx, stack);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测物品是否带有灵魂绑定附魔：
     * 1. 原版 PREVENT_EQUIPMENT_DROP 组件（Curse of Binding）
     * 2. yigd:soulbound 附魔 tag（含 YIGD 自身及兼容的各类灵魂绑定附魔）
     */
    private static boolean isSoulbound(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))
            return true;
        for (Holder<Enchantment> h : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
            if (h.is(YIGD_SOULBOUND_TAG)) return true;
        }
        return false;
    }
}
