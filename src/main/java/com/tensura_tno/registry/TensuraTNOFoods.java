package com.tensura_tno.registry;

import net.minecraft.world.food.FoodProperties;

public final class TensuraTNOFoods {

    public static final FoodProperties STORM_DAEMON_HIPOKUTE_SOUP =
            new FoodProperties.Builder()
                    .nutrition(20)
                    .saturationModifier(0.55F)
                    .alwaysEdible()
                    .build();

    public static final FoodProperties MONSTER_MEAT_PLATTER =
            new FoodProperties.Builder()
                    .nutrition(30)
                    .saturationModifier(0.55F)
                    .alwaysEdible()
                    .build();

    /** 史莱姆酱包：蒸笼蒸出的小食，饱食度中等，可随时食用。 */
    public static final FoodProperties SLIME_SAUCE_BUN =
            new FoodProperties.Builder()
                    .nutrition(6)
                    .saturationModifier(0.6F)
                    .alwaysEdible()
                    .build();

    /** 孤刃虎碎肉（生）：砧板切碎的中型猫科肉块。 */
    public static final FoodProperties BLADE_TIGER_MINCED_MEAT =
            new FoodProperties.Builder()
                    .nutrition(3)
                    .saturationModifier(0.3F)
                    .build();

    /** 熟孤刃虎碎肉。 */
    public static final FoodProperties COOKED_BLADE_TIGER_MINCED_MEAT =
            new FoodProperties.Builder()
                    .nutrition(8)
                    .saturationModifier(0.8F)
                    .build();

    /** 暴风大妖涡（卡律布狄斯）碎肉（生）：巨大海怪碎肉。 */
    public static final FoodProperties CHARYBDIS_MINCED_MEAT =
            new FoodProperties.Builder()
                    .nutrition(4)
                    .saturationModifier(0.4F)
                    .build();

    /** 熟卡律布狄斯碎肉。 */
    public static final FoodProperties COOKED_CHARYBDIS_MINCED_MEAT =
            new FoodProperties.Builder()
                    .nutrition(12)
                    .saturationModifier(1.0F)
                    .build();

    /** 装甲龙（铠龙）碎肉（生）：大型龙肉。 */
    public static final FoodProperties ARMORSAURUS_MINCED_MEAT =
            new FoodProperties.Builder()
                    .nutrition(4)
                    .saturationModifier(0.4F)
                    .build();

    /** 熟装甲龙碎肉。 */
    public static final FoodProperties COOKED_ARMORSAURUS_MINCED_MEAT =
            new FoodProperties.Builder()
                    .nutrition(12)
                    .saturationModifier(1.0F)
                    .build();

    /** 枪脚铠蜘蛛肉（生）。 */
    public static final FoodProperties KNIGHT_SPIDER_LEG_MEAT =
            new FoodProperties.Builder()
                    .nutrition(3)
                    .saturationModifier(0.3F)
                    .build();

    /** 熟枪脚铠蜘蛛肉。 */
    public static final FoodProperties COOKED_KNIGHT_SPIDER_LEG_MEAT =
            new FoodProperties.Builder()
                    .nutrition(8)
                    .saturationModifier(0.8F)
                    .build();

    /** 腊肉：暮色森林晒干架晒制 8 分钟得到的肉制品，营养与熟肉相当。 */
    public static final FoodProperties CURED_MEAT =
            new FoodProperties.Builder()
                    .nutrition(8)
                    .saturationModifier(0.8F)
                    .build();

    /** 切片腊肉：在砧板上切出的腊肉切片，分量小但更易携带与分次食用。 */
    public static final FoodProperties SLICED_CURED_MEAT =
            new FoodProperties.Builder()
                    .nutrition(4)
                    .saturationModifier(0.6F)
                    .build();

    private TensuraTNOFoods() {}
}
