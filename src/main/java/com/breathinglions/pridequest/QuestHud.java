package com.breathinglions.pridequest;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestHud {
    private static final int TEMP_TICKS = 20 * 8;

    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final Map<UUID, Long> temporaryUntil = new HashMap<>();
    private long tick = 0L;

    public void tick(Collection<ServerPlayer> players, QuestRepository repo) {
        tick++;

        for (ServerPlayer player : players) {
            PlayerQuestState state = repo.state(player);
            state.ensureTrackedQuest();

            if (!state.hasAnyQuest() || state.trackedQuest.isBlank()) {
                hide(player);
                temporaryUntil.remove(player.getUUID());
                continue;
            }

            long until = temporaryUntil.getOrDefault(player.getUUID(), 0L);
            if (until < tick) temporaryUntil.remove(player.getUUID());

            if (state.pinned || until >= tick) {
                showTracker(player, repo.getQuest(state.trackedQuest), state.active(state.trackedQuest));
            } else {
                hide(player);
            }
        }
    }

    public void questStarted(
            ServerPlayer player,
            QuestDefinition quest,
            PlayerQuestState.ActiveQuestState active) {
        sendTitle(
                player,
                Component.literal("NEW QUEST").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.literal(quest.title).withStyle(ChatFormatting.GOLD),
                8, 35, 8);

        temporaryUntil.put(player.getUUID(), tick + TEMP_TICKS);
        showTracker(player, quest, active);
    }

    public void objectiveAdvanced(
            ServerPlayer player,
            QuestDefinition quest,
            PlayerQuestState.ActiveQuestState active) {
        QuestDefinition.Objective objective = currentObjective(quest, active);
        if (objective == null) return;

        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(objective.text).withStyle(ChatFormatting.WHITE))
                        .append(progressComponent(active.progress, objective.target))
        ));

        temporaryUntil.put(player.getUUID(), tick + TEMP_TICKS);
        showTracker(player, quest, active);
    }

    /**
     * Normal objectives stay compact in the action bar. Big center-screen titles
     * are reserved for quest completions and future badge/champion milestones.
     */
    public void objectiveCompleted(ServerPlayer player, String text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal("✓ Objective complete: ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
        ));
    }

    public void questCompleted(ServerPlayer player, QuestDefinition quest) {
        sendTitle(
                player,
                Component.literal("QUEST COMPLETE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal(quest.title).withStyle(ChatFormatting.GOLD),
                8, 38, 8);
        hide(player);
    }

    public boolean togglePinned(ServerPlayer player, QuestRepository repo) {
        PlayerQuestState state = repo.state(player);
        state.ensureTrackedQuest();

        if (!state.hasAnyQuest() || state.trackedQuest.isBlank()) {
            state.pinned = false;
            repo.save(player);
            hide(player);
            player.sendSystemMessage(
                    Component.literal("No active PrideQuest objectives.").withStyle(ChatFormatting.GRAY));
            return false;
        }

        state.pinned = !state.pinned;
        repo.save(player);

        if (state.pinned) {
            showTracker(player, repo.getQuest(state.trackedQuest), state.active(state.trackedQuest));
            player.sendSystemMessage(
                    Component.literal("Quest tracker pinned. Use /q again to hide it.")
                            .withStyle(ChatFormatting.AQUA));
        } else {
            temporaryUntil.remove(player.getUUID());
            hide(player);
            player.sendSystemMessage(
                    Component.literal("Quest tracker hidden. Use /q to pin it.")
                            .withStyle(ChatFormatting.GRAY));
        }

        return state.pinned;
    }

    public void trackedQuestChanged(ServerPlayer player, QuestRepository repo) {
        PlayerQuestState state = repo.state(player);
        state.ensureTrackedQuest();

        if (state.trackedQuest.isBlank()) {
            hide(player);
            return;
        }

        temporaryUntil.put(player.getUUID(), tick + TEMP_TICKS);
        showTracker(player, repo.getQuest(state.trackedQuest), state.active(state.trackedQuest));
    }

    public void refresh(ServerPlayer player, QuestRepository repo) {
        PlayerQuestState state = repo.state(player);
        state.ensureTrackedQuest();

        if (state.pinned && !state.trackedQuest.isBlank()) {
            showTracker(player, repo.getQuest(state.trackedQuest), state.active(state.trackedQuest));
        }
    }

    public void hide(ServerPlayer player) {
        ServerBossEvent bar = bars.get(player.getUUID());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    private void showTracker(
            ServerPlayer player,
            QuestDefinition quest,
            PlayerQuestState.ActiveQuestState active) {
        QuestDefinition.Objective objective = currentObjective(quest, active);
        if (quest == null || objective == null || active == null) {
            hide(player);
            return;
        }

        ServerBossEvent bar = bars.computeIfAbsent(
                player.getUUID(),
                ignored -> new ServerBossEvent(
                        Component.literal("PrideQuest"),
                        BossEvent.BossBarColor.BLUE,
                        BossEvent.BossBarOverlay.PROGRESS));

        bar.setName(
                Component.literal("✦ " + quest.title + "  •  ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(objective.text).withStyle(ChatFormatting.WHITE))
                        .append(progressComponent(active.progress, objective.target)));

        bar.setProgress(Math.min(
                1.0F,
                (float) active.progress / (float) Math.max(1, objective.target)));
        bar.setVisible(true);
        bar.addPlayer(player);
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

    private Component progressComponent(int progress, int target) {
        if (target <= 1) return Component.empty();
        return Component.literal("  " + progress + "/" + target).withStyle(ChatFormatting.YELLOW);
    }

    private void sendTitle(
            ServerPlayer player,
            Component title,
            Component subtitle,
            int fadeIn,
            int stay,
            int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }
}
