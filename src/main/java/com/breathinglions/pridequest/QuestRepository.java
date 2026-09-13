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
import java.nio.file.*;
import java.util.*;

public final class QuestRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, QuestDefinition> quests = new HashMap<>();
    private final Map<UUID, PlayerQuestState> states = new HashMap<>();
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
        return quests.get(id);
    }

    public PlayerQuestState state(ServerPlayer player) {
        return states.computeIfAbsent(player.getUUID(), ignored -> new PlayerQuestState());
    }

    public void loadQuests() {
        quests.clear();
        try {
            Files.createDirectories(questDir());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(questDir(), "*.json")) {
                for (Path path : stream) {
                    try (Reader reader = Files.newBufferedReader(path)) {
                        QuestDefinition q = GSON.fromJson(reader, QuestDefinition.class);
                        if (q != null && q.id != null && !q.id.isBlank()) quests.put(q.id, q);
                    } catch (Exception e) {
                        PrideQuest.LOGGER.error("Failed to load quest {}", path, e);
                    }
                }
            }
            PrideQuest.LOGGER.info("Loaded {} PrideQuest definitions", quests.size());
        } catch (IOException e) {
            PrideQuest.LOGGER.error("Could not read PrideQuest configs", e);
        }
    }

    private void loadStates() {
        states.clear();
        try {
            Files.createDirectories(stateDir());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(stateDir(), "*.json")) {
                for (Path path : stream) {
                    try {
                        UUID uuid = UUID.fromString(path.getFileName().toString().replace(".json", ""));
                        try (Reader reader = Files.newBufferedReader(path)) {
                            PlayerQuestState state = GSON.fromJson(reader, PlayerQuestState.class);
                            if (state != null) states.put(uuid, state);
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
        try {
            Files.createDirectories(stateDir());
            try (Writer writer = Files.newBufferedWriter(stateDir().resolve(player.getUUID() + ".json"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(state(player), writer);
            }
        } catch (IOException e) {
            PrideQuest.LOGGER.error("Could not save PrideQuest state for {}", player.getUUID(), e);
        }
    }
}
