package com.tensura_tno.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tensura_tno.ability.skill.SpiritSummonSkill;
import com.tensura_tno.race.fox_spirit.SummonMaxEPRequirement;
import io.github.manasmods.manascore.race.api.RaceAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /tno admin summonep set all <amount> [player]
 * /tno admin summonep set <entity_id> <amount> [player]
 * /tno admin summonep add all <amount> [player]
 * /tno admin summonep add <entity_id> <amount> [player]
 * <p>
 * 设置或增加灵之召唤口袋中指定召唤物（或全部）的 bonus EP。
 * 需要 OP 权限（等级 2）。
 */
public final class SummonEPCommand {

    private SummonEPCommand() {}

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ENTITY_ID = (ctx, builder) -> {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return builder.buildFuture();
        }
        List<String> ids = SpiritSummonSkill.SpiritSummonPockets.getAbsorbedEntities(player);
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tno")
                .then(Commands.literal("admin")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("summonep")
                        .then(Commands.literal("set")
                            // /tno admin summonep set all <amount> [player]
                            .then(Commands.literal("all")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                    .executes(ctx -> executeAll(ctx, Mode.SET, null))
                                    .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeAll(ctx, Mode.SET,
                                            EntityArgument.getPlayer(ctx, "player")))
                                    )
                                )
                            )
                            // /tno admin summonep set <entity_id> <amount> [player]
                            .then(Commands.argument("entity_id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_ENTITY_ID)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                    .executes(ctx -> executeOne(ctx, Mode.SET, null))
                                    .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeOne(ctx, Mode.SET,
                                            EntityArgument.getPlayer(ctx, "player")))
                                    )
                                )
                            )
                        )
                        .then(Commands.literal("add")
                            // /tno admin summonep add all <amount> [player]
                            .then(Commands.literal("all")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                    .executes(ctx -> executeAll(ctx, Mode.ADD, null))
                                    .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeAll(ctx, Mode.ADD,
                                            EntityArgument.getPlayer(ctx, "player")))
                                    )
                                )
                            )
                            // /tno admin summonep add <entity_id> <amount> [player]
                            .then(Commands.argument("entity_id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_ENTITY_ID)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                    .executes(ctx -> executeOne(ctx, Mode.ADD, null))
                                    .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeOne(ctx, Mode.ADD,
                                            EntityArgument.getPlayer(ctx, "player")))
                                    )
                                )
                            )
                        )
                    )
                )
        );
    }

    private enum Mode { SET, ADD }

    private static int executeAll(CommandContext<CommandSourceStack> ctx, Mode mode,
                                  ServerPlayer explicitTarget) {
        return execute(ctx, mode, explicitTarget, "all");
    }

    private static int executeOne(CommandContext<CommandSourceStack> ctx, Mode mode,
                                  ServerPlayer explicitTarget) {
        ResourceLocation rl = ResourceLocationArgument.getId(ctx, "entity_id");
        return execute(ctx, mode, explicitTarget, rl.toString());
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, Mode mode,
                               ServerPlayer explicitTarget, String entityId) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target;
        try {
            target = explicitTarget != null ? explicitTarget : source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[TNO] 需要指定玩家或由玩家执行"));
            return 0;
        }

        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        List<String> absorbed = SpiritSummonSkill.SpiritSummonPockets.getAbsorbedEntities(target);
        if (absorbed.isEmpty()) {
            source.sendFailure(Component.literal("[TNO] 该玩家灵之召唤口袋为空"));
            return 0;
        }

        boolean isAll = entityId.equalsIgnoreCase("all");
        List<String> targets = isAll ? absorbed : List.of(entityId);

        if (!isAll && !absorbed.contains(entityId)) {
            source.sendFailure(Component.literal("[TNO] 口袋中不存在该实体: " + entityId));
            return 0;
        }

        double maxEP = 0;
        for (String eid : targets) {
            if (mode == Mode.SET) {
                SpiritSummonSkill.SpiritSummonPockets.setBonusEP(target, eid, amount);
            } else {
                SpiritSummonSkill.SpiritSummonPockets.addBonusEP(target, eid, amount);
            }
            double current = SpiritSummonSkill.SpiritSummonPockets.getBonusEP(target, eid);
            if (current > maxEP) maxEP = current;
        }

        // 同步最大 bonus EP 到种族 tag（进化条件用）
        final double finalMax = maxEP;
        RaceAPI.getRaceFrom(target).getRace()
            .ifPresent(instance -> SummonMaxEPRequirement.updateMaxSummonBonusEP(instance, finalMax));

        String modeStr = mode == Mode.SET ? "设置" : "增加";
        String targetStr = isAll ? "全部召唤物" : entityId;
        String msg = String.format("[TNO] 已%s %s 的 EP %s %.0f（当前最大: %.0f）",
                modeStr, targetStr, mode == Mode.SET ? "为" : "", amount, finalMax);
        source.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }
}
