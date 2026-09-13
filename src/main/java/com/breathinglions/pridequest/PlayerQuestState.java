package com.breathinglions.pridequest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PlayerQuestState {
    public static final int CURRENT_DATA_VERSION = 2;

    public int dataVersion = CURRENT_DATA_VERSION;
    public Map<String, ActiveQuestState> activeQuests = new LinkedHashMap<>();
    public String trackedQuest = "";
    public boolean pinned = false;
    public Set<String> completedQuests = new LinkedHashSet<>();
    public Map<String, Long> completionTimes = new LinkedHashMap<>();

    // v0.1 fields retained only so existing player files can migrate automatically.
    @Deprecated public String activeQuest = "";
    @Deprecated public int objectiveIndex = 0;
    @Deprecated public int progress = 0;

    public static final class ActiveQuestState {
        public int objectiveIndex = 0;
        public int progress = 0;
        public long startedAt = System.currentTimeMillis();

        public ActiveQuestState() {}

        public ActiveQuestState(int objectiveIndex, int progress) {
            this.objectiveIndex = Math.max(0, objectiveIndex);
            this.progress = Math.max(0, progress);
            this.startedAt = System.currentTimeMillis();
        }
    }

    public boolean migrateLegacy() {
        boolean changed = false;

        if (activeQuests == null) {
            activeQuests = new LinkedHashMap<>();
            changed = true;
        }
        if (completedQuests == null) {
            completedQuests = new LinkedHashSet<>();
            changed = true;
        }
        if (completionTimes == null) {
            completionTimes = new LinkedHashMap<>();
            changed = true;
        }
        if (trackedQuest == null) {
            trackedQuest = "";
            changed = true;
        }

        if (activeQuest != null && !activeQuest.isBlank() && !activeQuests.containsKey(activeQuest)) {
            activeQuests.put(activeQuest, new ActiveQuestState(objectiveIndex, progress));
            if (trackedQuest.isBlank()) trackedQuest = activeQuest;
            changed = true;
        }

        if (dataVersion != CURRENT_DATA_VERSION) {
            dataVersion = CURRENT_DATA_VERSION;
            changed = true;
        }

        if (activeQuest != null && !activeQuest.isBlank()) {
            activeQuest = "";
            objectiveIndex = 0;
            progress = 0;
            changed = true;
        }

        if (!trackedQuest.isBlank() && !activeQuests.containsKey(trackedQuest)) {
            trackedQuest = firstActiveQuest();
            changed = true;
        }

        return changed;
    }

    public boolean hasAnyQuest() {
        return activeQuests != null && !activeQuests.isEmpty();
    }

    public boolean hasQuest(String questId) {
        return questId != null && activeQuests != null && activeQuests.containsKey(questId);
    }

    public ActiveQuestState active(String questId) {
        return activeQuests == null ? null : activeQuests.get(questId);
    }

    public String firstActiveQuest() {
        if (activeQuests == null || activeQuests.isEmpty()) return "";
        return activeQuests.keySet().iterator().next();
    }

    public void ensureTrackedQuest() {
        if (trackedQuest == null) trackedQuest = "";
        if (trackedQuest.isBlank() || activeQuests == null || !activeQuests.containsKey(trackedQuest)) {
            trackedQuest = firstActiveQuest();
        }
    }

    public void resetAll() {
        if (activeQuests == null) activeQuests = new LinkedHashMap<>();
        if (completedQuests == null) completedQuests = new LinkedHashSet<>();
        if (completionTimes == null) completionTimes = new LinkedHashMap<>();

        activeQuests.clear();
        completedQuests.clear();
        completionTimes.clear();
        trackedQuest = "";
        pinned = false;

        activeQuest = "";
        objectiveIndex = 0;
        progress = 0;
        dataVersion = CURRENT_DATA_VERSION;
    }
}
