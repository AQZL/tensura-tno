package com.tensura_tno.item;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Simple bowl-returning food item, matching Kaleidoscope Cookery's BowlFoodOnlyItem behavior
 * without requiring a compile-time dependency on that mod's classes.
 */
public class TnoBowlFoodItem extends Item {

    public TnoBowlFoodItem(FoodProperties food) {
        super(new Item.Properties().stacksTo(16).food(new FoodProperties(
                food.nutrition(),
                food.saturation(),
                food.canAlwaysEat(),
                food.eatSeconds(),
                Optional.of(Items.BOWL.getDefaultInstance()),
                food.effects()
        )));
    }
}
