package com.tensura_tno.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tensura_tno.food.FoodEPConfig;
import com.tensura_tno.food.FoodEPManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Registers: /tno food ep <value> [mp <value>] [ap <value>]
 *
 * The targeted food item is whatever the executing player holds in their main hand.
 * Values may be plain numbers ("100") or percentages.
 *
 * For percentages, use 'p' suffix instead of '%':
 *   /tno food ep 1.5p          → 1.5%
 *   /tno food ep 500 mp 2p     → EP=500 flat, MP=2%
 *
 * Or wrap in quotes if you want literal '%':
 *   /tno food ep "1.5%"
 */
public final class FoodEPCommand {

    private FoodEPCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tno")
                .then(Commands.literal("food")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("ep")
                        .then(Commands.argument("ep_value", StringArgumentType.word())
                            .executes(ctx -> setFood(ctx,
                                normalize(StringArgumentType.getString(ctx, "ep_value")),
                                null, null))
                            .then(Commands.literal("mp")
                                .then(Commands.argument("mp_value", StringArgumentType.word())
                                    .executes(ctx -> setFood(ctx,
                                        normalize(StringArgumentType.getString(ctx, "ep_value")),
                                        normalize(StringArgumentType.getString(ctx, "mp_value")),
                                        null))
                                    .then(Commands.literal("ap")
                                        .then(Commands.argument("ap_value", StringArgumentType.word())
                                            .executes(ctx -> setFood(ctx,
                                                normalize(StringArgumentType.getString(ctx, "ep_value")),
                                                normalize(StringArgumentType.getString(ctx, "mp_value")),
                                                normalize(StringArgumentType.getString(ctx, "ap_value"))))
                                        )
                                    )
                                )
                            )
                            .then(Commands.literal("ap")
                                .then(Commands.argument("ap_value2", StringArgumentType.word())
                                    .executes(ctx -> setFood(ctx,
                                        normalize(StringArgumentType.getString(ctx, "ep_value")),
                                        null,
                                        normalize(StringArgumentType.getString(ctx, "ap_value2"))))
                                )
                            )
                        )
                    )
                )
        );
    }

    /** Convert trailing 'p' to '%' so users can type 1.5p instead of quoting "1.5%". */
    private static String normalize(String value) {
        if (value.endsWith("p") || value.endsWith("P")) {
            String num = value.substring(0, value.length() - 1);
            try {
                Double.parseDouble(num);
                return num + "%";
            } catch (NumberFormatException ignored) {}
        }
        return value;
    }

    private static int setFood(CommandContext<CommandSourceStack> ctx,
                               @Nullable String ep,
                               @Nullable String mp,
                               @Nullable String ap) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[TNO] 此指令需要玩家执行"));
            return 0;
        }

        ItemStack mainhand = player.getMainHandItem();
        if (mainhand.isEmpty()) {
            source.sendFailure(Component.literal("[TNO] 请手持食物物品"));
            return 0;
        }

        FoodProperties food = mainhand.getFoodProperties(player);
        if (food == null) {
            source.sendFailure(Component.literal("[TNO] 手持物品不是食物: "
                    + BuiltInRegistries.ITEM.getKey(mainhand.getItem())));
            return 0;
        }

        if (!isValidValue(ep) || !isValidValue(mp) || !isValidValue(ap)) {
            source.sendFailure(Component.literal(
                "[TNO] 值格式错误。请使用数字（如 100）或百分比（如 1.5p 代表 1.5%）"));
            return 0;
        }

        Item item = mainhand.getItem();
        FoodEPManager.setConfig(item, ep, mp, ap);

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        StringBuilder sb = new StringBuilder("[TNO] 已设置 ")
                .append(itemId)
                .append(" → EP: ").append(ep != null ? ep : "(默认饱食度×10)");
        if (mp != null) sb.append(", MP: ").append(mp);
        if (ap != null) sb.append(", AP: ").append(ap);

        final String msg = sb.toString();
        source.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static boolean isValidValue(@Nullable String value) {
        if (value == null) return true;
        try {
            if (FoodEPConfig.isPercent(value)) {
                Double.parseDouble(value.trim().replace("%", ""));
            } else {
                Double.parseDouble(value.trim());
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
