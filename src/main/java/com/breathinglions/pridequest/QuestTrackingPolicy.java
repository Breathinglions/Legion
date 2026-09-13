package com.breathinglions.pridequest;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * Keeps the main story feeling like the primary quest line without constantly
 * overriding a player's manual /q track choice.
 *
 * A newly started story quest receives one automatic chance to become tracked.
 * After that, the player is free to track a daily/weekly/side quest and the
 * policy will not fight them every tick.
 */
public final class QuestTrackingPolicy {
    private QuestTrackingPolicy() {}

    public static void tick(
            List<ServerPlayer> players,
            QuestRepository repo,
            QuestHud hud) {
        for (ServerPlayer player : players) {
            apply(player, repo, hud);
        }
    }

    private static void apply(
            ServerPlayer player,
            QuestRepository repo,
            QuestHud hud) {
        PlayerQuestState state = repo.state(player);
        if (state.activeQuests == null || state.activeQuests.isEmpty()) return;

        String newestUnhandledStory = "";
        long newestStartedAt = Long.MIN_VALUE;
        boolean dirty = false;

        for (Map.Entry<String, PlayerQuestState.ActiveQuestState> entry
                : state.activeQuests.entrySet()) {
            PlayerQuestState.ActiveQuestState active = entry.getValue();
            if (active == null || active.trackingPolicyHandled) continue;

            active.trackingPolicyHandled = true;
            dirty = true;

            QuestDefinition quest = repo.getQuest(entry.getKey());
            if (quest == null || quest.category == null
                    || !"story".equalsIgnoreCase(quest.category)) {
                continue;
            }

            if (active.startedAt >= newestStartedAt) {
                newestStartedAt = active.startedAt;
                newestUnhandledStory = entry.getKey();
            }
        }

        if (!newestUnhandledStory.isBlank()) {
            QuestDefinition currentlyTracked = repo.getQuest(state.trackedQuest);
            boolean trackingStory = state.hasQuest(state.trackedQuest)
                    && currentlyTracked != null
                    && "story".equalsIgnoreCase(currentlyTracked.category);

            if (!trackingStory) {
                state.trackedQuest = newestUnhandledStory;
                dirty = true;
                if (state.pinned) {
                    hud.trackedQuestChanged(player, repo);
                }
            }
        }

        if (dirty) repo.save(player);
    }
}
