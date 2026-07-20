package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.item.EnchantmentStoneItem;
import com.tensura_tno.item.ForbiddenSummonTomeItem;
import com.tensura_tno.item.RequiredQuestResetScrollItem;
import com.tensura_tno.item.TnoBowlFoodItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TensuraTNOItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, TensuraTNOMod.MOD_ID);

    public static final DeferredHolder<Item, EnchantmentStoneItem> ENCHANTMENT_STONE =
            ITEMS.register("enchantment_stone", () -> new EnchantmentStoneItem(
                    new Item.Properties()
                            .rarity(Rarity.EPIC)
                            .stacksTo(16)));

    /** 禁忌召唤术魔法书 — epic 稀有度，与巫师塔 epic tome 概率相同 (weight 3/118 ≈ 2.5%) */
    public static final DeferredHolder<Item, ForbiddenSummonTomeItem> FORBIDDEN_SUMMON_TOME =
            ITEMS.register("forbidden_summon_tome", ForbiddenSummonTomeItem::new);

    public static final DeferredHolder<Item, RequiredQuestResetScrollItem> REQUIRED_QUEST_RESET_SCROLL =
            ITEMS.register("required_quest_reset_scroll", RequiredQuestResetScrollItem::new);

    public static final DeferredHolder<Item, TnoBowlFoodItem> STORM_DAEMON_HIPOKUTE_SOUP =
            ITEMS.register("storm_daemon_hipokute_soup",
                    () -> new TnoBowlFoodItem(TensuraTNOFoods.STORM_DAEMON_HIPOKUTE_SOUP));

    public static final DeferredHolder<Item, TnoBowlFoodItem> MONSTER_MEAT_PLATTER =
            ITEMS.register("monster_meat_platter",
                    () -> new TnoBowlFoodItem(TensuraTNOFoods.MONSTER_MEAT_PLATTER));

    /**
     * 史莱姆酱包：蒸笼配方产物。普通食物（不返回容器，最大堆叠 64）。
     */
    public static final DeferredHolder<Item, Item> SLIME_SAUCE_BUN =
            ITEMS.register("slime_sauce_bun",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.SLIME_SAUCE_BUN)));

    /** 孤刃虎碎肉（生）：砧板切碎，可用熔炉/烟熏炉/营火烹饪。 */
    public static final DeferredHolder<Item, Item> BLADE_TIGER_MINCED_MEAT =
            ITEMS.register("blade_tiger_minced_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.BLADE_TIGER_MINCED_MEAT)));

    public static final DeferredHolder<Item, Item> COOKED_BLADE_TIGER_MINCED_MEAT =
            ITEMS.register("cooked_blade_tiger_minced_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.COOKED_BLADE_TIGER_MINCED_MEAT)));

    /** 暴风大妖涡（卡律布狄斯）碎肉（生）。 */
    public static final DeferredHolder<Item, Item> CHARYBDIS_MINCED_MEAT =
            ITEMS.register("charybdis_minced_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.CHARYBDIS_MINCED_MEAT)));

    public static final DeferredHolder<Item, Item> COOKED_CHARYBDIS_MINCED_MEAT =
            ITEMS.register("cooked_charybdis_minced_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.COOKED_CHARYBDIS_MINCED_MEAT)));

    /** 装甲龙（铠龙）碎肉（生）。 */
    public static final DeferredHolder<Item, Item> ARMORSAURUS_MINCED_MEAT =
            ITEMS.register("armorsaurus_minced_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.ARMORSAURUS_MINCED_MEAT)));

    public static final DeferredHolder<Item, Item> COOKED_ARMORSAURUS_MINCED_MEAT =
            ITEMS.register("cooked_armorsaurus_minced_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.COOKED_ARMORSAURUS_MINCED_MEAT)));

    /** 枪脚铠蜘蛛肉（生）。 */
    public static final DeferredHolder<Item, Item> KNIGHT_SPIDER_LEG_MEAT =
            ITEMS.register("knight_spider_leg_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.KNIGHT_SPIDER_LEG_MEAT)));

    public static final DeferredHolder<Item, Item> COOKED_KNIGHT_SPIDER_LEG_MEAT =
            ITEMS.register("cooked_knight_spider_leg_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.COOKED_KNIGHT_SPIDER_LEG_MEAT)));

    /** 腊肉：通过暮色森林晒干架晒制 8 分钟而得，9 类原料各成一份配方（鱼肉拆为鳕鱼/鲑鱼）。 */
    public static final DeferredHolder<Item, Item> CURED_MEAT =
            ITEMS.register("cured_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.CURED_MEAT)));

    /** 切片腊肉：在砧板上切碎腊肉得到，4 刀产 2 个，便于携带和分次食用。 */
    public static final DeferredHolder<Item, Item> SLICED_CURED_MEAT =
            ITEMS.register("sliced_cured_meat",
                    () -> new Item(new Item.Properties().food(TensuraTNOFoods.SLICED_CURED_MEAT)));

    public static final DeferredHolder<Item, BlockItem> TNO_STOVE =
            ITEMS.register("tno_stove", () -> new BlockItem(TensuraTNOBlocks.TNO_STOVE.get(), new Item.Properties()));

    private TensuraTNOItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
