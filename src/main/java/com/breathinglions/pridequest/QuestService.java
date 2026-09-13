package com.breathinglions.pridequest;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class QuestService {
    private final QuestRepository repo;
    private final QuestHud hud;

    public QuestService(QuestRepository repo, QuestHud hud) {
        this.repo = repo;
        this.hud = hud;
    }

    public boolean give(ServerPlayer player, String questId, boolean force) {
        QuestDefinition quest = repo.getQuest(questId);
        if (quest == null) return false;
        PlayerQuestState state = repo.state(player);
        if (state.hasQuest() && !force) return false;
        state.activeQuest = quest.id;
        state.objectiveIndex = 0;
        state.progress = 0;
        repo.save(player);
        hud.questStarted(player, quest, state);
        return true;
    }

    public boolean trigger(ServerPlayer player, String trigger, int amount) {
        PlayerQuestState state = repo.state(player);
        QuestDefinition quest = currentQuest(state);
        QuestDefinition.Objective objective = currentObjective(quest, state);
        if (objective == null || !"manual".equalsIgnoreCase(objective.type)) return false;
        if (objective.trigger == null || !objective.trigger.equalsIgnoreCase(trigger)) return false;
        advance(player, Math.max(1, amount));
        return true;
    }

    public void event(ServerPlayer player, String type) {
        PlayerQuestState state = repo.state(player);
        QuestDefinition quest = currentQuest(state);
        QuestDefinition.Objective objective = currentObjective(quest, state);
        if (objective == null || !type.equalsIgnoreCase(objective.type)) return;
        advance(player, 1);
    }

    public void advance(ServerPlayer player, int amount) {
        PlayerQuestState state = repo.state(player);
        QuestDefinition quest = currentQuest(state);
        QuestDefinition.Objective objective = currentObjective(quest, state);
        if (objective == null) return;

        state.progress += amount;
        if (state.progress >= Math.max(1, objective.target)) {
            hud.objectiveCompleted(player, objective.text);
            state.objectiveIndex++;
            state.progress = 0;
            if (state.objectiveIndex >= quest.objectives.size()) {
                complete(player);
                return;
            }
        }

        repo.save(player);
        hud.objectiveAdvanced(player, quest, state);
    }

    public void complete(ServerPlayer player) {
        PlayerQuestState state = repo.state(player);
        QuestDefinition quest = currentQuest(state);
        if (quest == null) return;

        runCompletionCommands(player, quest);
        hud.questCompleted(player, quest);

        String next = quest.nextQuest == null ? "" : quest.nextQuest.trim();
        boolean auto = quest.autoStartNext && !next.isBlank();
        state.clearQuest();
        repo.save(player);
        if (auto) give(player, next, true);
    }

    public void reset(ServerPlayer player) {
        PlayerQuestState state = repo.state(player);
        state.clearQuest();
        state.pinned = false;
        repo.save(player);
        hud.hide(player);
    }

    public Component status(ServerPlayer player) {
        PlayerQuestState state = repo.state(player);
        QuestDefinition quest = currentQuest(state);
        QuestDefinition.Objective objective = currentObjective(quest, state);
        if (quest == null || objective == null) {
            return Component.literal("No active PrideQuest.").withStyle(ChatFormatting.GRAY);
        }
        return Component.literal(quest.title + " — ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(objective.text).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + state.progress + "/" + Math.max(1, objective.target) + ")").withStyle(ChatFormatting.YELLOW));
    }

    public void refresh(ServerPlayer player) {
        PlayerQuestState state = repo.state(player);
        QuestDefinition quest = currentQuest(state);
        if (quest != null) hud.refresh(player, quest, state);
    }

    private QuestDefinition currentQuest(PlayerQuestState state) {
        if (state == null || !state.hasQuest()) return null;
        return repo.getQuest(state.activeQuest);
    }

    private QuestDefinition.Objective currentObjective(QuestDefinition quest, PlayerQuestState state) {
        if (quest == null || quest.objectives == null || quest.objectives.isEmpty()) return null;
        if (state.objectiveIndex < 0 || state.objectiveIndex >= quest.objectives.size()) return null;
        return quest.objectives.get(state.objectiveIndex);
    }

    private void runCompletionCommands(ServerPlayer player, QuestDefinition quest) {
        if (quest.completionCommands == null || quest.completionCommands.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        for (String raw : quest.completionCommands) {
            if (raw == null || raw.isBlank()) continue;
            String command = raw.replace("{player}", player.getGameProfile().getName());
            if (command.startsWith("/")) command = command.substring(1);
            server.getCommands().performPrefixedCommand(source, command);
        }
    }
}
