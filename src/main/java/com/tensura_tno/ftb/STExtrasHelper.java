package com.tensura_tno.ftb;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 通过反射调用 STExtras API，避免编译期对 STExtras jar 的强依赖。
 * 若 STExtras 未加载，所有方法均静默失败并返回安全默认值。
 */
public final class STExtrasHelper {

    // ---------- RE (Reincarnation Essence) ----------
    private static Method GET_RE;
    private static Method SET_RE;

    // ---------- Soul Grade ----------
    private static Method GET_SOUL_GRADE;
    private static Method SET_SOUL_GRADE;

    // ---------- Quests ----------
    private static Method ACTIVE_QUESTS;
    private static Method COMPLETED_QUESTS;
    private static Method CAN_PRESTIGE;
    private static Method REQUEST_PRESTIGE_SCREEN_SYNC;
    private static Method GET_PLAYER_QUESTS;
    private static Method QUESTS_TO_NET;
    private static Method QUESTS_CLEAR_DIRTY;
    private static Method SYNC_QUESTS_WITH_RACE_IDS;
    private static Method APPLY_REQUIRED_ON_CHARACTER_RESET;
    private static Object CAT_DAILY;
    private static Object CAT_WEEKLY;
    private static Object CAT_REQUIRED;

    // ---------- Events（用于修复 CONSUME 追踪 bug）----------
    private static Method FIRE_CONSUME;

    private static boolean loaded = false;

    static {
        try {
            Class<?> playerStorage = Class.forName("org.crypticdev.stextras.storage.STExtarsStorage$Player");
            GET_RE = playerStorage.getMethod("getReincarnationEssence", ServerPlayer.class);
            SET_RE = playerStorage.getMethod("setReincarnationEssence", ServerPlayer.class, int.class);
            GET_SOUL_GRADE = playerStorage.getMethod("getSoulGrade", ServerPlayer.class);
            SET_SOUL_GRADE = playerStorage.getMethod("setSoulGrade", ServerPlayer.class, int.class);

            Class<?> questsStorage = Class.forName("org.crypticdev.stextras.storage.STExtarsStorage$Quests");
            Class<?> categoryClass  = Class.forName("org.crypticdev.stextras.quest.definition.QuestCategory");
            ACTIVE_QUESTS    = questsStorage.getMethod("active",    ServerPlayer.class, categoryClass);
            COMPLETED_QUESTS = questsStorage.getMethod("completed", ServerPlayer.class, categoryClass);

            Class<?> prestigeUtils = Class.forName("org.crypticdev.stextras.utils.PrestigeUtils");
            CAN_PRESTIGE = prestigeUtils.getMethod("canPrestige", ServerPlayer.class);

            @SuppressWarnings("unchecked")
            Class<Enum> catEnum = (Class<Enum>) categoryClass;
            CAT_DAILY    = Enum.valueOf(catEnum, "DAILY");
            CAT_WEEKLY   = Enum.valueOf(catEnum, "WEEKLY");
            CAT_REQUIRED = Enum.valueOf(catEnum, "REQUIRED");

            Class<?> stPlayerEvents = Class.forName("org.crypticdev.stextras.event.STPlayerEvents");
            FIRE_CONSUME = stPlayerEvents.getMethod("fireConsume", ServerPlayer.class, ItemStack.class);

            loaded = true;
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.warn("[TNO] STExtrasHelper 初始化失败（STExtras 未加载？）: {}", e.getMessage());
        }

        try {
            Class<?> questAccess = Class.forName("org.crypticdev.stextras.storage.quest.ISTExtrasQuests");
            Class<?> questStorage = Class.forName("org.crypticdev.stextras.storage.quest.STExtrasQuestStorage");
            GET_PLAYER_QUESTS = questAccess.getMethod("stextras$getQuests");
            QUESTS_TO_NET = questStorage.getMethod("toNet");
            QUESTS_CLEAR_DIRTY = questStorage.getMethod("clearDirty");
        } catch (Exception ignored) {}

        try {
            Class<?> openPrestigeScreenPacket = Class.forName("org.crypticdev.stextras.net.C2S.OpenPrestigeScreenPacket");
            REQUEST_PRESTIGE_SCREEN_SYNC = openPrestigeScreenPacket.getMethod("sendToServer");
        } catch (Exception ignored) {}

        try {
            Class<?> questSync = Class.forName("org.crypticdev.stextras.net.STExtrasQuestSync");
            SYNC_QUESTS_WITH_RACE_IDS = questSync.getMethod("sendWithRaceIds", ServerPlayer.class, CompoundTag.class);
        } catch (Exception ignored) {}

        try {
            Class<?> requiredSelector = Class.forName("org.crypticdev.stextras.quest.assignment.RequiredQuestSelector");
            APPLY_REQUIRED_ON_CHARACTER_RESET = requiredSelector.getMethod("applyRequiredOnCharacterReset",
                    ServerPlayer.class, int.class);
        } catch (Exception ignored) {}
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** 获取玩家当前 RE 值，未加载则返回 0 */
    public static int getRE(ServerPlayer player) {
        if (!loaded) return 0;
        try {
            return (int) GET_RE.invoke(null, player);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 设置玩家 RE 值 */
    public static void setRE(ServerPlayer player, int value) {
        if (!loaded) return;
        try {
            SET_RE.invoke(null, player, value);
        } catch (Exception ignored) {}
    }

    /** 获取玩家当前灵魂点数（Soul Grade），未加载则返回 0 */
    public static int getSoulGrade(ServerPlayer player) {
        if (!loaded) return 0;
        try {
            return (int) GET_SOUL_GRADE.invoke(null, player);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 设置玩家灵魂点数 */
    public static void setSoulGrade(ServerPlayer player, int value) {
        if (!loaded) return;
        try {
            SET_SOUL_GRADE.invoke(null, player, value);
        } catch (Exception ignored) {}
    }

    /**
     * 获取玩家指定类别的「活跃委托」集合。
     * 若 STExtras 未加载或出错，返回 null（而非空集合），以便调用方区分"出错"与"空"。
     *
     * @param category "DAILY" / "WEEKLY" / "REQUIRED"
     * @return 活跃委托集合，或 null（STExtras 未加载 / 调用出错）
     */
    @SuppressWarnings("unchecked")
    public static Set<ResourceLocation> getActiveQuests(ServerPlayer player, String category) {
        if (!loaded) return null;
        try {
            Object catEnum = resolveCategory(category);
            if (catEnum == null) return null;
            return (Set<ResourceLocation>) ACTIVE_QUESTS.invoke(null, player, catEnum);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取玩家指定类别的「已完成委托」集合。
     * 若 STExtras 未加载或出错，返回 null。
     *
     * @param category "DAILY" / "WEEKLY" / "REQUIRED"
     * @return 已完成委托集合，或 null（STExtras 未加载 / 调用出错）
     */
    @SuppressWarnings("unchecked")
    public static Set<ResourceLocation> getCompletedQuests(ServerPlayer player, String category) {
        if (!loaded) return null;
        try {
            Object catEnum = resolveCategory(category);
            if (catEnum == null) return null;
            return (Set<ResourceLocation>) COMPLETED_QUESTS.invoke(null, player, catEnum);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 使用 STExtras 自己的声望判定作为 Reset Counter 是否完成的唯一依据。
     * 不调用 Tensura 的 ResetScrollItem.isFullReset()，因为 STExtras 会 mixin 该方法并触发 doPrestige() 副作用。
     */
    public static boolean canPrestige(ServerPlayer player) {
        if (!loaded || CAN_PRESTIGE == null || player == null) return false;
        try {
            return (boolean) CAN_PRESTIGE.invoke(null, player);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 客户端请求 STExtras 把服务端任务状态同步回来。
     * 这等价于打开 STExtras 声望界面时发送的 open_prestige_screen 包。
     */
    public static void requestPrestigeScreenSync() {
        if (REQUEST_PRESTIGE_SCREEN_SYNC == null) return;
        try {
            REQUEST_PRESTIGE_SCREEN_SYNC.invoke(null);
        } catch (Exception ignored) {}
    }

    /**
     * 服务端主动把 STExtras 任务状态同步给该玩家。
     * 用于 REQUIRED 必要任务刚刷新/分配后，避免 Tensura 主界面继续读客户端旧缓存。
     */
    public static boolean refreshRequiredQuests(ServerPlayer player, int prestige) {
        if (!loaded || APPLY_REQUIRED_ON_CHARACTER_RESET == null || player == null) return false;
        try {
            APPLY_REQUIRED_ON_CHARACTER_RESET.invoke(null, player, prestige);
            syncQuestState(player);
            return true;
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.warn("[TNO] Failed to refresh STExtras required quests: {}", e.getMessage());
            return false;
        }
    }

    public static void syncQuestState(ServerPlayer player) {
        if (player == null || GET_PLAYER_QUESTS == null || QUESTS_TO_NET == null || SYNC_QUESTS_WITH_RACE_IDS == null) {
            return;
        }

        try {
            Object store = GET_PLAYER_QUESTS.invoke(player);
            if (store == null) return;

            CompoundTag questData = (CompoundTag) QUESTS_TO_NET.invoke(store);
            SYNC_QUESTS_WITH_RACE_IDS.invoke(null, player, questData);

            if (QUESTS_CLEAR_DIRTY != null) {
                QUESTS_CLEAR_DIRTY.invoke(store);
            }
        } catch (Exception ignored) {}
    }

    /**
     * 在物品消耗<b>前</b>（count 尚未归零时）触发 STExtras 的 CONSUME 事件。
     *
     * <p>STExtras 原本在 {@code ItemStack.finishUsingItem} 的 TAIL 处触发该事件，
     * 但此时物品 count 已被 {@code ItemStack.consume()} 减为 0，导致
     * {@code food.getCount()} = 0，进而 delta = 0，委托进度永远无法增加。
     * 本方法由 {@code STExtrasConsumeFixMixin} 在 HEAD 处调用，此时 count 仍为 1，
     * 可正确触发任务进度。</p>
     */
    public static void fireConsumeEarly(ServerPlayer player, ItemStack stack) {
        if (!loaded || FIRE_CONSUME == null) return;
        try {
            FIRE_CONSUME.invoke(null, player, stack);
        } catch (Exception ignored) {}
    }

    private static Object resolveCategory(String category) {
        return switch (category) {
            case "WEEKLY"   -> CAT_WEEKLY;
            case "REQUIRED" -> CAT_REQUIRED;
            default         -> CAT_DAILY;
        };
    }

    private STExtrasHelper() {}
}
