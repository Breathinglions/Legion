package com.breathinglions.pridequest;

import java.util.ArrayList;
import java.util.List;

public final class QuestDefinition {
    public int schemaVersion = 2;
    public String id;
    public String title;
    public String description = "";
    public String category = "story";
    public boolean repeatable = false;
    public List<String> prerequisites = new ArrayList<>();
    public boolean autoStartNext = true;
    public String nextQuest = "";
    public String pool = "";
    public int weight = 1;
    public ResetPolicy reset = new ResetPolicy();
    public List<Objective> objectives = new ArrayList<>();
    public List<Reward> rewards = new ArrayList<>();

    /**
     * Legacy v0.1 reward hook. Kept for backward-compatible quest configs.
     * New configs should prefer the structured rewards array.
     */
    public List<String> completionCommands = new ArrayList<>();

    public static final class Objective {
        public String id;
        public String type;
        public String text;
        public int target = 1;
        public String trigger = "";

        // Future/filtered Cobblemon objectives can use these without changing the file format.
        public String species = "";
        public String pokemonType = "";
        public String item = "";
        public String biome = "";
        public String npc = "";

        // Server-side location objective fields.
        public String dimension = "";
        public Double x;
        public Double y;
        public Double z;
        public double radius = 4.0D;
    }

    public static final class Reward {
        public String type = "";
        public String message = "";
        public String command = "";
        public String item = "";
        public int amount = 1;
    }

    public static final class ResetPolicy {
        public String type = "none";
        public String dayOfWeek = "MONDAY";
        public int hour = 0;
        public String timezone = "UTC";
    }
}
