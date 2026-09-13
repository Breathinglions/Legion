package com.breathinglions.pridequest;

import java.util.ArrayList;
import java.util.List;

public final class QuestDefinition {
    public String id;
    public String title;
    public String description = "";
    public boolean autoStartNext = true;
    public String nextQuest = "";
    public List<Objective> objectives = new ArrayList<>();
    public List<String> completionCommands = new ArrayList<>();

    public static final class Objective {
        public String id;
        public String type;
        public String text;
        public int target = 1;
        public String trigger = "";
    }
}
