package com.breathinglions.pridequest;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class QuestService {
    public enum GiveResult {
        STARTED,
        UNKNOWN_QUEST,
        ALREADY_ACTIVE,
        MISSING_PREREQUISITE,
        ALREADY_COMPLETED,
        RESET_NOT_READY
    }

    private final QuestRepository repo;
    private final QuestHud hud;
    private long tickCounter = 0L;

    public QuestService(QuestRepository repo, QuestHud hud) {
        this.repo = repo;
        this.hud = hud;
    }

    public GiveResult give(ServerPlayer player, String questId, boolean force) {
        QuestDefinition quest = repo.getQuest(questId);
        if (quest == null) return GiveResult.UNKNOWN_QUEST;

        PlayerQuestState state = repo.state(player);
        GiveResult eligibility = canGive(state, quest, force);
        if (eligibility != GiveResult.STARTED) return eligibility;

        state.activeQuests.put(quest.id, new PlayerQuestState.ActiveQuestState());

        if (state.trackedQuest == null || state.trackedQuest.isBlank()) {
            state.trackedQuest = quest.id;
        }

        repo.save(player);
        hud.questStarted(player, quest, state.active(quest.id));
        return GiveResult.STARTED;
    }

    private GiveResult canGive(PlayerQuestState state, QuestDefinition quest, boolean force) {
        if (quest == null) return GiveResult.UNKNOWN_QUEST;

        if (state.hasQuest(quest.id)) {
            if (!force) return GiveResult.ALREADY_ACTIVE;
            return GiveResult.STARTED;
        }

        if (force) return GiveResult.STARTED;

        if (quest.prerequisites != null) {
            for (String prerequisite : quest.prerequisites) {
                if (prerequisite == null || prerequisite.isBlank()) continue;
                if (!state.completedQuests.contains(prerequisite)) {
                    return GiveResult.MISSING_PREREQUISITE;
                }
            }
        }

        if (state.completedQuests.contains(quest.id)) {
            if (!quest.repeatable) return GiveResult.ALREADY_COMPLETED;
            if (!resetReady(state, quest, System.currentTimeMillis())) {
                return GiveResult.RESET_NOT_READY;
            }
        }

        return GiveResult.STARTED;
    }

    public int trigger(ServerPlayer player, String trigger, int amount) {
        PlayerQuestState state = repo.state(player);
        List<String> activeIds = new ArrayList<>(state.activeQuests.keySet());
        int advanced = 0;

        for (String questId : activeIds) {
            QuestDefinition quest = repo.getQuest(questId);
            PlayerQuestState.ActiveQuestState active = state.active(questId);
            QuestDefinition.Objective objective = currentObjective(quest, active);

            if (objective == null || !"manual".equalsIgnoreCase(objective.type)) continue;
            if (objective.trigger == null || !objective.trigger.equalsIgnoreCase(trigger)) continue;

            advanceQuest(player, questId, Math.max(1, amount));
            advanced++;
        }

        return advanced;
    }

    /**
     * A single Cobblemon event can progress story + daily + weekly quests at the same time.
     */
    public int event(ServerPlayer player, String type) {
        PlayerQuestState state = repo.state(player);
        List<String> activeIds = new ArrayList<>(state.activeQuests.keySet());
        int advanced = 0;

        for (String questId : activeIds) {
            QuestDefinition quest = repo.getQuest(questId);
            PlayerQuestState.ActiveQuestState active = state.active(questId);
            QuestDefinition.Objective objective = currentObjective(quest, active);

            if (objective == null || !type.equalsIgnoreCase(objective.type)) continue;

            advanceQuest(player, questId, 1);
            advanced++;
        }

        return advanced;
    }

    public boolean advanceTracked(ServerPlayer player, int amount) {
        PlayerQuestState state = repo.state(player);
        state.ensureTrackedQuest();
        if (state.trackedQuest.isBlank()) return false;
        return advanceQuest(player, state.trackedQuest, Math.max(1, amount));
    }

    public boolean advanceQuest(ServerPlayer player, String questId, int amount) {
        PlayerQuestState state = repo.state(player);
        QuestDefinition quest = repo.getQuest(questId);
        PlayerQuestState.ActiveQuestState active = state.active(questId);
        QuestDefinition.Objective objective = currentObjective(quest, active);

        if (quest == null || active == null || objective == null) return false;

        active.progress += Math.max(1, amount);

        if (active.progress >= Math.max(1, objective.target)) {
            hud.objectiveCompleted(player, objective.text);
            active.objectiveIndex++;
            active.progress = 0;

            if (active.objectiveIndex >= quest.objectives.size()) {
                completeQuest(player, questId);
                return true;
            }
        }

        repo.save(player);
        hud.objectiveAdvanced(player, quest, active);
        return true;
    }

    public boolean completeTracked(ServerPlayer player) {
        PlayerQuestState state = repo.state(player);
        state.ensureTrackedQuest();
        if (state.trackedQuest.isBlank()) return false;
        return completeQuest(player, state.trackedQuest);
    }

    public boolean completeQuest(ServerPlayer player, String questId) {
        PlayerQuestState state = repo.state(player);
        QuestDefinition quest = repo.getQuest(questId);
        if (quest == null || !state.hasQuest(questId)) return false;

        runRewards(player, quest);
        runLegacyCompletionCommands(player, quest);

        state.completedQuests.add(quest.id);
        state.completionTimes.put(quest.id, System.currentTimeMillis());
        state.activeQuests.remove(quest.id);

        if (quest.id.equals(state.trackedQuest)) {
            state.trackedQuest = state.firstActiveQuest();
        }

        repo.save(player);
        hud.questCompleted(player, quest);

        String next = quest.nextQuest == null ? "" : quest.nextQuest.trim();
        if (quest.autoStartNext && !next.isBlank()) {
            GiveResult nextResult = give(player, next, false);
            if (nextResult != GiveResult.STARTED) {
                PrideQuest.LOGGER.warn(
                        "Could not auto-start next quest '{}' for {}: {}",
                        next,
                        player.getGameProfile().getName(),
                        nextResult);
            }
        }

        return true;
    }

    /**
     * Admin fresh-player reset: active quests, history, repeat timers and tracker state.
     */
    public void resetAll(ServerPlayer player) {
        PlayerQuestState state = repo.state(player);
        state.resetAll();
        repo.save(player);
        hud.hide(player);
    }

    public boolean resetQuest(ServerPlayer player, String questId) {
        PlayerQuestState state = repo.state(player);
        boolean changed = false;

        if (state.activeQuests.remove(questId) != null) changed = true;
        if (state.completedQuests.remove(questId)) changed = true;
        if (state.completionTimes.remove(questId) != null) changed = true;

        if (questId.equals(state.trackedQuest)) {
            state.trackedQuest = state.firstActiveQuest();
            changed = true;
        }

        if (changed) {
            repo.save(player);
            hud.hide(player);
            if (state.pinned && !state.trackedQuest.isBlank()) hud.trackedQuestChanged(player, repo);
        }

        return changed;
    }

    public boolean track(ServerPlayer player, String questId) {
        PlayerQuestState state = repo.state(player);
        if (!state.hasQuest(questId)) return false;

        state.trackedQuest = questId;
        state.pinned = true;
        repo.save(player);
        hud.trackedQuestChanged(player, repo);
        return true;
    }

    public Component status(ServerPlayer player) {
        PlayerQuestState state = repo.state(player);
        state.ensureTrackedQuest();

        if (state.trackedQuest.isBlank()) {
            return Component.literal("No active PrideQuest.").withStyle(ChatFormatting.GRAY);
        }

        QuestDefinition quest = repo.getQuest(state.trackedQuest);
        PlayerQuestState.ActiveQuestState active = state.active(state.trackedQuest);
        QuestDefinition.Objective objective = currentObjective(quest, active);

        if (quest == null || objective == null || active == null) {
            return Component.literal("No active PrideQuest.").withStyle(ChatFormatting.GRAY);
        }

        MutableComponent line = Component.literal(quest.title + " — ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(objective.text).withStyle(ChatFormatting.WHITE));

        if (objective.target > 1) {
            line.append(Component.literal(
                    " (" + active.progress + "/" + objective.target + ")").withStyle(ChatFormatting.YELLOW));
        }

        return line;
    }

    public Component categoryStatus(ServerPlayer player, String category) {
        PlayerQuestState state = repo.state(player);
        String wanted = category == null ? "story" : category.toLowerCase(Locale.ROOT);

        MutableComponent result = Component.literal(
                "PrideQuest • " + capitalize(wanted)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);

        int found = 0;
        for (String questId : state.activeQuests.keySet()) {
            QuestDefinition quest = repo.getQuest(questId);
            if (quest == null || quest.category == null || !quest.category.equalsIgnoreCase(wanted)) continue;

            PlayerQuestState.ActiveQuestState active = state.active(questId);
            QuestDefinition.Objective objective = currentObjective(quest, active);
            if (objective == null) continue;

            found++;
            result.append(Component.literal("\n• ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(quest.title).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" — " + objective.text).withStyle(ChatFormatting.WHITE));

            if (objective.target > 1) {
                result.append(Component.literal(
                        " " + active.progress + "/" + objective.target).withStyle(ChatFormatting.YELLOW));
            }

            if (questId.equals(state.trackedQuest)) {
                result.append(Component.literal(" [TRACKED]").withStyle(ChatFormatting.AQUA));
            }
        }

        if (found == 0) {
            result.append(Component.literal("\nNo active " + wanted + " quests.").withStyle(ChatFormatting.GRAY));
        }

        return result;
    }

    public Component history(ServerPlayer player) {
        PlayerQuestState state = repo.state(player);
        MutableComponent result = Component.literal("PrideQuest • Completed")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);

        if (state.completedQuests.isEmpty()) {
            return result.append(Component.literal("\nNo completed quests yet.").withStyle(ChatFormatting.GRAY));
        }

        int shown = 0;
        for (String questId : state.completedQuests) {
            QuestDefinition quest = repo.getQuest(questId);
            String title = quest == null ? questId : quest.title;
            result.append(Component.literal("\n✓ " + title).withStyle(ChatFormatting.GRAY));
            shown++;
            if (shown >= 15) {
                if (state.completedQuests.size() > shown) {
                    result.append(Component.literal(
                            "\n…and " + (state.completedQuests.size() - shown) + " more.")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                break;
            }
        }

        return result;
    }

    public int rollPool(ServerPlayer player, String pool, int count) {
        List<QuestDefinition> candidates = new ArrayList<>();
        PlayerQuestState state = repo.state(player);

        for (QuestDefinition quest : repo.byPool(pool)) {
            if (canGive(state, quest, false) == GiveResult.STARTED) candidates.add(quest);
        }

        int started = 0;
        int desired = Math.max(1, count);

        while (started < desired && !candidates.isEmpty()) {
            QuestDefinition selected = weightedPick(candidates);
            candidates.remove(selected);

            if (give(player, selected.id, false) == GiveResult.STARTED) started++;
        }

        return started;
    }

    public void tick(List<ServerPlayer> players) {
        tickCounter++;
        if (tickCounter % 10L != 0L) return;

        for (ServerPlayer player : players) {
            PlayerQuestState state = repo.state(player);
            List<String> activeIds = new ArrayList<>(state.activeQuests.keySet());

            for (String questId : activeIds) {
                QuestDefinition quest = repo.getQuest(questId);
                PlayerQuestState.ActiveQuestState active = state.active(questId);
                QuestDefinition.Objective objective = currentObjective(quest, active);

                if (objective == null || !isLocationType(objective.type)) continue;
                if (objective.x == null || objective.y == null || objective.z == null) continue;

                if (objective.dimension != null && !objective.dimension.isBlank()) {
                    String playerDimension = player.level().dimension().location().toString();
                    if (!playerDimension.equalsIgnoreCase(objective.dimension)) continue;
                }

                double dx = player.getX() - objective.x;
                double dy = player.getY() - objective.y;
                double dz = player.getZ() - objective.z;
                double radius = objective.radius <= 0.0D ? 4.0D : objective.radius;

                if ((dx * dx) + (dy * dy) + (dz * dz) <= radius * radius) {
                    advanceQuest(player, questId, 1);
                }
            }
        }
    }

    public void refresh(ServerPlayer player) {
        hud.refresh(player, repo);
    }

    private QuestDefinition.Objective currentObjective(
            QuestDefinition quest,
            PlayerQuestState.ActiveQuestState active) {
        if (quest == null || active == null || quest.objectives == null || quest.objectives.isEmpty()) {
            return null;
        }
        if (active.objectiveIndex < 0 || active.objectiveIndex >= quest.objectives.size()) return null;
        return quest.objectives.get(active.objectiveIndex);
    }

    private boolean isLocationType(String type) {
        return "location".equalsIgnoreCase(type) || "reach_location".equalsIgnoreCase(type);
    }

    private QuestDefinition weightedPick(List<QuestDefinition> candidates) {
        int total = 0;
        for (QuestDefinition quest : candidates) total += Math.max(1, quest.weight);

        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        for (QuestDefinition quest : candidates) {
            roll -= Math.max(1, quest.weight);
            if (roll < 0) return quest;
        }

        return candidates.get(0);
    }

    private boolean resetReady(PlayerQuestState state, QuestDefinition quest, long now) {
        if (!quest.repeatable) return false;
        if (quest.reset == null || quest.reset.type == null
                || quest.reset.type.isBlank() || "none".equalsIgnoreCase(quest.reset.type)) {
            return true;
        }

        long completedAt = state.completionTimes.getOrDefault(quest.id, 0L);
        if (completedAt <= 0L) return true;

        long boundary = latestResetBoundary(now, quest.reset);
        return completedAt < boundary;
    }

    private long latestResetBoundary(long nowMillis, QuestDefinition.ResetPolicy reset) {
        ZoneId zone;
        try {
            zone = ZoneId.of(reset.timezone == null || reset.timezone.isBlank() ? "UTC" : reset.timezone);
        } catch (Exception ignored) {
            zone = ZoneId.of("UTC");
        }

        int hour = Math.max(0, Math.min(23, reset.hour));
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        String type = reset.type == null ? "none" : reset.type.toLowerCase(Locale.ROOT);

        if ("daily".equals(type)) {
            ZonedDateTime boundary = now.toLocalDate().atTime(hour, 0).atZone(zone);
            if (boundary.isAfter(now)) boundary = boundary.minusDays(1);
            return boundary.toInstant().toEpochMilli();
        }

        if ("weekly".equals(type)) {
            DayOfWeek resetDay;
            try {
                resetDay = DayOfWeek.valueOf(
                        reset.dayOfWeek == null ? "MONDAY" : reset.dayOfWeek.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                resetDay = DayOfWeek.MONDAY;
            }

            LocalDate date = now.toLocalDate().with(TemporalAdjusters.previousOrSame(resetDay));
            ZonedDateTime boundary = date.atTime(hour, 0).atZone(zone);
            if (boundary.isAfter(now)) boundary = boundary.minusWeeks(1);
            return boundary.toInstant().toEpochMilli();
        }

        return 0L;
    }

    private void runRewards(ServerPlayer player, QuestDefinition quest) {
        if (quest.rewards == null || quest.rewards.isEmpty()) return;

        for (QuestDefinition.Reward reward : quest.rewards) {
            if (reward == null || reward.type == null) continue;
            String type = reward.type.toLowerCase(Locale.ROOT);

            switch (type) {
                case "message" -> {
                    if (reward.message != null && !reward.message.isBlank()) {
                        player.sendSystemMessage(
                                Component.literal(replacePlayer(reward.message, player))
                                        .withStyle(ChatFormatting.GOLD));
                    }
                }
                case "item" -> {
                    if (reward.item != null && reward.item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                        executeAsServer(
                                player,
                                "give " + player.getGameProfile().getName() + " "
                                        + reward.item + " " + Math.max(1, reward.amount));
                    }
                }
                case "xp" -> executeAsServer(
                        player,
                        "experience add " + player.getGameProfile().getName() + " "
                                + Math.max(1, reward.amount) + " points");
                case "command" -> {
                    if (reward.command != null && !reward.command.isBlank()) {
                        executeAsServer(player, replacePlayer(reward.command, player));
                    }
                }
                default -> PrideQuest.LOGGER.warn(
                        "Quest '{}' has unsupported reward type '{}'", quest.id, reward.type);
            }
        }
    }

    private void runLegacyCompletionCommands(ServerPlayer player, QuestDefinition quest) {
        if (quest.completionCommands == null || quest.completionCommands.isEmpty()) return;

        for (String raw : quest.completionCommands) {
            if (raw == null || raw.isBlank()) continue;
            executeAsServer(player, replacePlayer(raw, player));
        }
    }

    private String replacePlayer(String raw, ServerPlayer player) {
        return raw.replace("{player}", player.getGameProfile().getName());
    }

    private void executeAsServer(ServerPlayer player, String rawCommand) {
        MinecraftServer server = player.getServer();
        if (server == null || rawCommand == null || rawCommand.isBlank()) return;

        String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        CommandSourceStack source = server.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return "Story";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
