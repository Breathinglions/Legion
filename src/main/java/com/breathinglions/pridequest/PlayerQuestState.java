package com.breathinglions.pridequest;

public final class PlayerQuestState {
    public String activeQuest = "";
    public int objectiveIndex = 0;
    public int progress = 0;
    public boolean pinned = false;

    public boolean hasQuest() {
        return activeQuest != null && !activeQuest.isBlank();
    }

    public void clearQuest() {
        activeQuest = "";
        objectiveIndex = 0;
        progress = 0;
    }
}
