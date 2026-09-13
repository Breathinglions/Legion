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

import java.util.*;

public final class QuestHud {
    private static final int TEMP_TICKS = 20 * 8;
    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final Map<UUID, Long> temporaryUntil = new HashMap<>();
    private long tick = 0;

    public void tick(Collection<ServerPlayer> players, QuestRepository repo) {
        tick++;
        for (ServerPlayer player : players) {
            PlayerQuestState state = repo.state(player);
            if (!state.hasQuest()) {
                hide(player);
                continue;
            }
            if (state.pinned || temporaryUntil.getOrDefault(player.getUUID(), 0L) >= tick) {
                showTracker(player, repo.getQuest(state.activeQuest), state);
            } else {
                hide(player);
            }
        }
    }

    public void questStarted(ServerPlayer player, QuestDefinition quest, PlayerQuestState state) {
        sendTitle(player,
                Component.literal("QUEST STARTED").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.literal(quest.title).withStyle(ChatFormatting.GOLD));
        temporaryUntil.put(player.getUUID(), tick + TEMP_TICKS);
        showTracker(player, quest, state);
    }

    public void objectiveAdvanced(ServerPlayer player, QuestDefinition quest, PlayerQuestState state) {
        QuestDefinition.Objective obj = currentObjective(quest, state);
        if (obj == null) return;
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(obj.text).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  " + state.progress + "/" + Math.max(1, obj.target)).withStyle(ChatFormatting.YELLOW))
        ));
        if (state.pinned || temporaryUntil.getOrDefault(player.getUUID(), 0L) >= tick) showTracker(player, quest, state);
    }

    public void objectiveCompleted(ServerPlayer player, String text) {
        sendTitle(player,
                Component.literal("OBJECTIVE COMPLETE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    public void questCompleted(ServerPlayer player, QuestDefinition quest) {
        sendTitle(player,
                Component.literal("QUEST COMPLETE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal(quest.title).withStyle(ChatFormatting.GOLD));
        hide(player);
    }

    public void togglePinned(ServerPlayer player, QuestRepository repo) {
        PlayerQuestState state = repo.state(player);
        state.pinned = !state.pinned;
        repo.save(player);
        if (state.pinned && state.hasQuest()) {
            showTracker(player, repo.getQuest(state.activeQuest), state);
            player.sendSystemMessage(Component.literal("Quest tracker pinned. Use /q again to hide it.").withStyle(ChatFormatting.AQUA));
        } else {
            hide(player);
            player.sendSystemMessage(Component.literal("Quest tracker hidden. Use /q to pin it.").withStyle(ChatFormatting.GRAY));
        }
    }

    public void refresh(ServerPlayer player, QuestDefinition quest, PlayerQuestState state) {
        if (state.pinned) showTracker(player, quest, state);
    }

    public void hide(ServerPlayer player) {
        ServerBossEvent bar = bars.get(player.getUUID());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    private void showTracker(ServerPlayer player, QuestDefinition quest, PlayerQuestState state) {
        QuestDefinition.Objective obj = currentObjective(quest, state);
        if (quest == null || obj == null) {
            hide(player);
            return;
        }
        ServerBossEvent bar = bars.computeIfAbsent(player.getUUID(), ignored -> new ServerBossEvent(
                Component.literal("PrideQuest"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(Component.literal("✦ " + quest.title + "  •  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(obj.text).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  " + state.progress + "/" + Math.max(1, obj.target)).withStyle(ChatFormatting.YELLOW)));
        bar.setProgress(Math.min(1.0F, (float) state.progress / (float) Math.max(1, obj.target)));
        bar.setVisible(true);
        bar.addPlayer(player);
    }

    private QuestDefinition.Objective currentObjective(QuestDefinition quest, PlayerQuestState state) {
        if (quest == null || quest.objectives == null || quest.objectives.isEmpty()) return null;
        if (state.objectiveIndex < 0 || state.objectiveIndex >= quest.objectives.size()) return null;
        return quest.objectives.get(state.objectiveIndex);
    }

    private void sendTitle(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 45, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }
}
