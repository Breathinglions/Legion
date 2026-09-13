package com.breathinglions.pridequest;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class PrideQuestCommands {
    private PrideQuestCommands() {}

    public static void register(QuestRepository repo, QuestService service, QuestHud hud) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("q")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                hud.togglePinned(player, repo);
                                return 1;
                            })
                            .then(Commands.literal("status")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        ctx.getSource().sendSuccess(() -> service.status(player), false);
                                        return 1;
                                    }))
                            .then(categoryCommand("story", service))
                            .then(categoryCommand("side", service))
                            .then(categoryCommand("daily", service))
                            .then(categoryCommand("weekly", service))
                            .then(categoryCommand("endgame", service))
                            .then(categoryCommand("event", service))
                            .then(Commands.literal("history")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        ctx.getSource().sendSuccess(() -> service.history(player), false);
                                        return 1;
                                    }))
                            .then(Commands.literal("track")
                                    .then(Commands.argument("quest", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                String questId = StringArgumentType.getString(ctx, "quest");
                                                if (!service.track(player, questId)) {
                                                    ctx.getSource().sendFailure(Component.literal(
                                                            "That quest is not active: " + questId));
                                                    return 0;
                                                }
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("Now tracking " + questId + ".")
                                                                .withStyle(ChatFormatting.AQUA),
                                                        false);
                                                return 1;
                                            })))
            );

            dispatcher.register(
                    Commands.literal("pq")
                            .requires(src -> src.hasPermission(2))
                            .then(Commands.literal("reload")
                                    .executes(ctx -> {
                                        repo.loadQuests();
                                        int issues = repo.validationIssues().size();
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal(
                                                        "PrideQuest configs reloaded: "
                                                                + repo.questIds().size()
                                                                + " quest(s), "
                                                                + issues
                                                                + " validation issue(s).")
                                                        .withStyle(issues == 0
                                                                ? ChatFormatting.GREEN
                                                                : ChatFormatting.YELLOW),
                                                false);
                                        return issues == 0 ? 1 : 0;
                                    }))
                            .then(Commands.literal("validate")
                                    .executes(ctx -> {
                                        if (repo.validationIssues().isEmpty()) {
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal(
                                                            "PrideQuest validation passed. "
                                                                    + repo.questIds().size()
                                                                    + " quest(s) loaded.")
                                                            .withStyle(ChatFormatting.GREEN),
                                                    false);
                                            return 1;
                                        }

                                        MutableComponent output = Component.literal(
                                                        "PrideQuest validation: "
                                                                + repo.validationIssues().size()
                                                                + " issue(s)")
                                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
                                        int shown = 0;
                                        for (String issue : repo.validationIssues()) {
                                            output.append(Component.literal("\n• " + issue)
                                                    .withStyle(ChatFormatting.GRAY));
                                            shown++;
                                            if (shown >= 10) break;
                                        }
                                        if (repo.validationIssues().size() > shown) {
                                            output.append(Component.literal(
                                                            "\n…and "
                                                                    + (repo.validationIssues().size() - shown)
                                                                    + " more. Check server log.")
                                                    .withStyle(ChatFormatting.DARK_GRAY));
                                        }
                                        ctx.getSource().sendSuccess(() -> output, false);
                                        return 0;
                                    }))
                            .then(Commands.literal("list")
                                    .executes(ctx -> {
                                        MutableComponent output = Component.literal(
                                                        "PrideQuest quests (" + repo.questIds().size() + "): ")
                                                .withStyle(ChatFormatting.AQUA);
                                        if (repo.questIds().isEmpty()) {
                                            output.append(Component.literal("none")
                                                    .withStyle(ChatFormatting.GRAY));
                                        } else {
                                            output.append(Component.literal(
                                                            String.join(", ", repo.questIds()))
                                                    .withStyle(ChatFormatting.WHITE));
                                        }
                                        ctx.getSource().sendSuccess(() -> output, false);
                                        return 1;
                                    }))
                            .then(Commands.literal("give")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("quest", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String questId = StringArgumentType.getString(ctx, "quest");
                                                        QuestService.GiveResult result =
                                                                service.give(player, questId, false);
                                                        return reportGiveResult(ctx.getSource(), questId, result);
                                                    }))))
                            .then(Commands.literal("forcegive")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("quest", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String questId = StringArgumentType.getString(ctx, "quest");
                                                        QuestService.GiveResult result =
                                                                service.give(player, questId, true);
                                                        return reportGiveResult(ctx.getSource(), questId, result);
                                                    }))))
                            .then(Commands.literal("trigger")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("trigger", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String trigger = StringArgumentType.getString(ctx, "trigger");
                                                        int count = service.trigger(player, trigger, 1);
                                                        if (count == 0) {
                                                            ctx.getSource().sendFailure(Component.literal(
                                                                    "No active objective matched trigger '"
                                                                            + trigger + "'."));
                                                            return 0;
                                                        }
                                                        return count;
                                                    })
                                                    .then(Commands.argument(
                                                                    "amount",
                                                                    IntegerArgumentType.integer(1))
                                                            .executes(ctx -> {
                                                                ServerPlayer player =
                                                                        EntityArgument.getPlayer(ctx, "player");
                                                                String trigger =
                                                                        StringArgumentType.getString(ctx, "trigger");
                                                                int amount =
                                                                        IntegerArgumentType.getInteger(ctx, "amount");
                                                                int count =
                                                                        service.trigger(player, trigger, amount);
                                                                if (count == 0) {
                                                                    ctx.getSource().sendFailure(Component.literal(
                                                                            "No active objective matched trigger '"
                                                                                    + trigger + "'."));
                                                                    return 0;
                                                                }
                                                                return count;
                                                            })))))
                            .then(Commands.literal("advance")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                if (!service.advanceTracked(
                                                        EntityArgument.getPlayer(ctx, "player"), 1)) {
                                                    ctx.getSource().sendFailure(
                                                            Component.literal("Player has no tracked quest."));
                                                    return 0;
                                                }
                                                return 1;
                                            })))
                            .then(Commands.literal("complete")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                if (!service.completeTracked(
                                                        EntityArgument.getPlayer(ctx, "player"))) {
                                                    ctx.getSource().sendFailure(
                                                            Component.literal("Player has no tracked quest."));
                                                    return 0;
                                                }
                                                return 1;
                                            })))
                            .then(Commands.literal("reset")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                service.resetAll(player);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal(
                                                                        "Reset all PrideQuest progress for "
                                                                                + player.getGameProfile().getName()
                                                                                + ".")
                                                                .withStyle(ChatFormatting.YELLOW),
                                                        false);
                                                return 1;
                                            })))
                            .then(Commands.literal("resetquest")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("quest", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String questId = StringArgumentType.getString(ctx, "quest");
                                                        if (!service.resetQuest(player, questId)) {
                                                            ctx.getSource().sendFailure(Component.literal(
                                                                    "No saved progress found for " + questId + "."));
                                                            return 0;
                                                        }
                                                        return 1;
                                                    }))))
                            .then(Commands.literal("track")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("quest", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String questId = StringArgumentType.getString(ctx, "quest");
                                                        if (!service.track(player, questId)) {
                                                            ctx.getSource().sendFailure(Component.literal(
                                                                    "Quest is not active: " + questId));
                                                            return 0;
                                                        }
                                                        return 1;
                                                    }))))
                            .then(Commands.literal("roll")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("pool", StringArgumentType.word())
                                                    .then(Commands.argument(
                                                                    "count",
                                                                    IntegerArgumentType.integer(1, 20))
                                                            .executes(ctx -> {
                                                                ServerPlayer player =
                                                                        EntityArgument.getPlayer(ctx, "player");
                                                                String pool =
                                                                        StringArgumentType.getString(ctx, "pool");
                                                                int count =
                                                                        IntegerArgumentType.getInteger(ctx, "count");
                                                                int started =
                                                                        service.rollPool(player, pool, count);
                                                                ctx.getSource().sendSuccess(
                                                                        () -> Component.literal(
                                                                                        "Started "
                                                                                                + started
                                                                                                + " quest(s) from pool '"
                                                                                                + pool
                                                                                                + "'.")
                                                                                .withStyle(ChatFormatting.GREEN),
                                                                        false);
                                                                return started;
                                                            })))))
                            .then(Commands.literal("status")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                ctx.getSource().sendSuccess(
                                                        () -> service.status(player), false);
                                                return 1;
                                            })))
            );
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    categoryCommand(String category, QuestService service) {
        return Commands.literal(category)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ctx.getSource().sendSuccess(
                            () -> service.categoryStatus(player, category), false);
                    return 1;
                });
    }

    private static int reportGiveResult(
            net.minecraft.commands.CommandSourceStack source,
            String questId,
            QuestService.GiveResult result) {
        if (result == QuestService.GiveResult.STARTED) return 1;

        String message = switch (result) {
            case UNKNOWN_QUEST -> "Unknown quest: " + questId;
            case ALREADY_ACTIVE -> "Quest is already active: " + questId;
            case MISSING_PREREQUISITE -> "Quest prerequisites are not complete: " + questId;
            case ALREADY_COMPLETED -> "Quest has already been completed: " + questId;
            case RESET_NOT_READY -> "Repeatable quest is not ready to reset yet: " + questId;
            case STARTED -> "";
        };

        source.sendFailure(Component.literal(message));
        return 0;
    }
}
