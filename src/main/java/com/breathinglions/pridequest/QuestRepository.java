package com.breathinglions.pridequest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QuestRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();
    private final Map<UUID, PlayerQuestState> states = new LinkedHashMap<>();
    private final List<String> validationIssues = new ArrayList<>();
    private MinecraftServer server;

    public void attachServer(MinecraftServer server) {
        this.server = server;
        loadStates();
    }

    public Path questDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("pridequest").resolve("quests");
    }

    public Path stateDir() {
        if (server == null) throw new IllegalStateException("Server not attached yet");
        return server.getWorldPath(LevelResource.ROOT).resolve("pridequest").resolve("players");
    }

    public QuestDefinition getQuest(String id) {
        if (id == null) return null;
        return quests.get(id);
    }

    public Collection<QuestDefinition> allQuests() {
        return List.copyOf(quests.values());
    }

    public Set<String> questIds() {
        return new LinkedHashSet<>(quests.keySet());
    }

    public List<QuestDefinition> byCategory(String category) {
        String wanted = normalizeCategory(category);
        return quests.values().stream()
                .filter(q -> normalizeCategory(q.category).equals(wanted))
                .sorted(Comparator.comparing(q -> q.title == null ? q.id : q.title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<QuestDefinition> byPool(String pool) {
        if (pool == null || pool.isBlank()) return List.of();
        return quests.values().stream()
                .filter(q -> q.pool != null && q.pool.equalsIgnoreCase(pool))
                .toList();
    }

    public List<String> validationIssues() {
        return List.copyOf(validationIssues);
    }

    public PlayerQuestState state(ServerPlayer player) {
        PlayerQuestState state = states.computeIfAbsent(player.getUUID(), ignored -> new PlayerQuestState());
        state.migrateLegacy();
        return state;
    }

    public void loadQuests() {
        quests.clear();
        validationIssues.clear();

        try {
            Files.createDirectories(questDir());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(questDir(), "*.json")) {
                for (Path path : stream) loadQuestFile(path);
            }

            validateReferences();
            PrideQuest.LOGGER.info("Loaded {} PrideQuest definitions with {} validation issue(s)",
                    quests.size(), validationIssues.size());

            for (String issue : validationIssues) {
                PrideQuest.LOGGER.warn("[PrideQuest validation] {}", issue);
            }
        } catch (IOException e) {
            PrideQuest.LOGGER.error("Could not read PrideQuest configs", e);
        }
    }

    private void loadQuestFile(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            QuestDefinition quest = GSON.fromJson(reader, QuestDefinition.class);
            if (quest == null) {
                validationIssues.add(path.getFileName() + ": file did not contain a quest object.");
                return;
            }

            normalizeQuest(quest);
            List<String> hardErrors = validateQuest(path, quest);
            if (!hardErrors.isEmpty()) {
                validationIssues.addAll(hardErrors);
                return;
            }

            if (quests.containsKey(quest.id)) {
                validationIssues.add(path.getFileName() + ": duplicate quest id '" + quest.id + "'.");
                return;
            }

            quests.put(quest.id, quest);
        } catch (Exception e) {
            validationIssues.add(path.getFileName() + ": could not parse JSON (" + e.getMessage() + ").");
            PrideQuest.LOGGER.error("Failed to load quest {}", path, e);
        }
    }

    private void normalizeQuest(QuestDefinition quest) {
        if (quest.category == null || quest.category.isBlank()) quest.category = "story";
        quest.category = normalizeCategory(quest.category);
        if (quest.description == null) quest.description = "";
        if (quest.nextQuest == null) quest.nextQuest = "";
        if (quest.pool == null) quest.pool = "";
        if (quest.prerequisites == null) quest.prerequisites = new ArrayList<>();
        if (quest.objectives == null) quest.objectives = new ArrayList<>();
        if (quest.rewards == null) quest.rewards = new ArrayList<>();
        if (quest.completionCommands == null) quest.completionCommands = new ArrayList<>();
        if (quest.reset == null) quest.reset = new QuestDefinition.ResetPolicy();
        if (quest.weight < 1) quest.weight = 1;

        for (QuestDefinition.Objective objective : quest.objectives) {
            if (objective == null) continue;
            if (objective.type == null) objective.type = "";
            if (objective.text == null) objective.text = "";
            if (objective.trigger == null) objective.trigger = "";
            if (objective.dimension == null) objective.dimension = "";
            if (objective.target < 1) objective.target = 1;
            if (objective.radius <= 0.0D) objective.radius = 4.0D;
        }
    }

    private List<String> validateQuest(Path path, QuestDefinition quest) {
        List<String> errors = new ArrayList<>();
        String file = path.getFileName().toString();

        if (quest.id == null || quest.id.isBlank()) {
            errors.add(file + ": missing required 'id'.");
            return errors;
        }
        if (!quest.id.matches("[a-z0-9_\\-.]+")) {
            errors.add(file + ": quest id '" + quest.id + "' may only contain lowercase letters, numbers, _, - and .");
        }
        if (quest.title == null || quest.title.isBlank()) {
            errors.add(file + " [" + quest.id + "]: missing required 'title'.");
        }
        if (quest.objectives.isEmpty()) {
            errors.add(file + " [" + quest.id + "]: must contain at least one objective.");
        }

        Set<String> objectiveIds = new LinkedHashSet<>();
        for (int i = 0; i < quest.objectives.size(); i++) {
            QuestDefinition.Objective objective = quest.objectives.get(i);
            String where = file + " [" + quest.id + "] objective #" + (i + 1);

            if (objective == null) {
                errors.add(where + ": objective is null.");
                continue;
            }
            if (objective.id == null || objective.id.isBlank()) {
                errors.add(where + ": missing 'id'.");
            } else if (!objectiveIds.add(objective.id)) {
                errors.add(where + ": duplicate objective id '" + objective.id + "'.");
            }
            if (objective.type == null || objective.type.isBlank()) {
                errors.add(where + ": missing 'type'.");
            }
            if (objective.text == null || objective.text.isBlank()) {
                errors.add(where + ": missing player-facing 'text'.");
            }
            if ("manual".equalsIgnoreCase(objective.type)
                    && (objective.trigger == null || objective.trigger.isBlank())) {
                errors.add(where + ": manual objectives require 'trigger'.");
            }
            if (isLocationType(objective.type)
                    && (objective.x == null || objective.y == null || objective.z == null)) {
                errors.add(where + ": location objectives require x, y and z.");
            }
        }

        return errors;
    }

    private void validateReferences() {
        for (QuestDefinition quest : quests.values()) {
            if (quest.nextQuest != null && !quest.nextQuest.isBlank() && !quests.containsKey(quest.nextQuest)) {
                validationIssues.add("Quest '" + quest.id + "' references missing nextQuest '" + quest.nextQuest + "'.");
            }
            for (String prerequisite : quest.prerequisites) {
                if (prerequisite != null && !prerequisite.isBlank() && !quests.containsKey(prerequisite)) {
                    validationIssues.add("Quest '" + quest.id + "' references missing prerequisite '" + prerequisite + "'.");
                }
            }

            String resetType = quest.reset == null || quest.reset.type == null
                    ? "none" : quest.reset.type.toLowerCase(Locale.ROOT);
            if (!Set.of("none", "daily", "weekly").contains(resetType)) {
                validationIssues.add("Quest '" + quest.id + "' has unsupported reset type '" + resetType + "'.");
            }
        }
    }

    private boolean isLocationType(String type) {
        return "location".equalsIgnoreCase(type) || "reach_location".equalsIgnoreCase(type);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "story";
        return category.trim().toLowerCase(Locale.ROOT);
    }

    private void loadStates() {
        states.clear();

        try {
            Files.createDirectories(stateDir());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(stateDir(), "*.json")) {
                for (Path path : stream) {
                    try {
                        UUID uuid = UUID.fromString(path.getFileName().toString().replace(".json", ""));
                        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                            PlayerQuestState state = GSON.fromJson(reader, PlayerQuestState.class);
                            if (state == null) continue;
                            boolean migrated = state.migrateLegacy();
                            states.put(uuid, state);
                            if (migrated) saveState(uuid, state);
                        }
                    } catch (Exception e) {
                        PrideQuest.LOGGER.warn("Skipping invalid quest state {}", path, e);
                    }
                }
            }
        } catch (IOException e) {
            PrideQuest.LOGGER.error("Could not load PrideQuest player states", e);
        }
    }

    public void save(ServerPlayer player) {
        saveState(player.getUUID(), state(player));
    }

    public void saveAll(Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) save(player);
    }

    private void saveState(UUID uuid, PlayerQuestState state) {
        try {
            Files.createDirectories(stateDir());

            Path target = stateDir().resolve(uuid + ".json");
            Path temp = stateDir().resolve(uuid + ".json.tmp");
            Path backup = stateDir().resolve(uuid + ".json.bak");

            try (Writer writer = Files.newBufferedWriter(
                    temp,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                GSON.toJson(state, writer);
            }

            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            PrideQuest.LOGGER.error("Could not save PrideQuest state for {}", uuid, e);
        }
    }
}
