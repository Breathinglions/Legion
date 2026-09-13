package com.breathinglions.pridequest;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
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
            );

            dispatcher.register(
                    Commands.literal("pq")
                            .requires(src -> src.hasPermission(2))
                            .then(Commands.literal("reload")
                                    .executes(ctx -> {
                                        repo.loadQuests();
                                        ctx.getSource().sendSuccess(() -> Component.literal("PrideQuest configs reloaded.").withStyle(ChatFormatting.GREEN), false);
                                        return 1;
                                    }))
                            .then(Commands.literal("give")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("quest", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String quest = StringArgumentType.getString(ctx, "quest");
                                                        if (!service.give(player, quest, true)) {
                                                            ctx.getSource().sendFailure(Component.literal("Unknown quest: " + quest));
                                                            return 0;
                                                        }
                                                        return 1;
                                                    }))))
                            .then(Commands.literal("trigger")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("trigger", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String trigger = StringArgumentType.getString(ctx, "trigger");
                                                        return service.trigger(player, trigger, 1) ? 1 : 0;
                                                    })
                                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                            .executes(ctx -> {
                                                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                                String trigger = StringArgumentType.getString(ctx, "trigger");
                                                                return service.trigger(player, trigger, IntegerArgumentType.getInteger(ctx, "amount")) ? 1 : 0;
                                                            })))))
                            .then(Commands.literal("advance")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                service.advance(EntityArgument.getPlayer(ctx, "player"), 1);
                                                return 1;
                                            })))
                            .then(Commands.literal("complete")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                service.complete(EntityArgument.getPlayer(ctx, "player"));
                                                return 1;
                                            })))
                            .then(Commands.literal("reset")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                service.reset(EntityArgument.getPlayer(ctx, "player"));
                                                return 1;
                                            })))
                            .then(Commands.literal("status")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                ctx.getSource().sendSuccess(() -> service.status(player), false);
                                                return 1;
                                            })))
            );
        });
    }
}
