package com.breathinglions.pridequest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Seeds a usable starter quest pack on first boot without overwriting normal
 * administrator-edited configs. It also performs a narrowly-scoped migration
 * for the known PrideQuest v0.1 First Steps file used during early testing.
 */
public final class DefaultQuestInstaller {
    private static final List<String> DEFAULT_FILES = List.of(
            "first_steps.json",
            "road_to_thorn.json",
            "thorn_challenge.json",
            "daily_catch_5.json",
            "weekly_apricorn_25.json"
    );

    private DefaultQuestInstaller() {}

    public static void seed(Path questDir) {
        try {
            Files.createDirectories(questDir);
        } catch (IOException e) {
            PrideQuest.LOGGER.error("Could not create PrideQuest quest directory", e);
            return;
        }

        ClassLoader loader = DefaultQuestInstaller.class.getClassLoader();
        migrateKnownV01FirstSteps(questDir, loader);

        int created = 0;
        for (String fileName : DEFAULT_FILES) {
            Path target = questDir.resolve(fileName);
            if (Files.exists(target)) continue;

            if (copyBundled(loader, fileName, target, false)) {
                created++;
                PrideQuest.LOGGER.info("Seeded PrideQuest config {}", target.getFileName());
            }
        }

        if (created > 0) {
            PrideQuest.LOGGER.info("Seeded {} PrideQuest default quest config(s)", created);
        }
    }

    /**
     * The v0.1 prototype shipped one oversized first_steps quest containing
     * road_to_thorn and first_badge objectives. v0.2 splits those into separate
     * chapters. Only that exact legacy shape is migrated. A .bak copy is kept.
     */
    private static void migrateKnownV01FirstSteps(Path questDir, ClassLoader loader) {
        Path current = questDir.resolve("first_steps.json");
        if (!Files.exists(current)) return;

        try {
            String raw = Files.readString(current, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) return;

            JsonObject root = parsed.getAsJsonObject();
            if (!"first_steps".equals(string(root, "id"))) return;

            int schemaVersion = root.has("schemaVersion") && root.get("schemaVersion").isJsonPrimitive()
                    ? root.get("schemaVersion").getAsInt()
                    : 1;
            if (schemaVersion >= 2) return;

            JsonArray objectives = root.has("objectives") && root.get("objectives").isJsonArray()
                    ? root.getAsJsonArray("objectives")
                    : new JsonArray();

            boolean hasRoadToThorn = false;
            boolean hasFirstBadge = false;
            for (JsonElement element : objectives) {
                if (!element.isJsonObject()) continue;
                String objectiveId = string(element.getAsJsonObject(), "id");
                if ("road_to_thorn".equals(objectiveId)) hasRoadToThorn = true;
                if ("first_badge".equals(objectiveId)) hasFirstBadge = true;
            }

            if (!hasRoadToThorn || !hasFirstBadge) return;

            Path backup = questDir.resolve("first_steps.v0.1.bak");
            if (!Files.exists(backup)) {
                Files.copy(current, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }

            if (copyBundled(loader, "first_steps.json", current, true)) {
                PrideQuest.LOGGER.info(
                        "Migrated legacy v0.1 first_steps.json to the v0.2 chapter format; backup saved as {}",
                        backup.getFileName());
            }
        } catch (Exception e) {
            PrideQuest.LOGGER.warn(
                    "Could not inspect legacy first_steps.json for automatic migration; leaving it unchanged",
                    e);
        }
    }

    private static boolean copyBundled(
            ClassLoader loader,
            String fileName,
            Path target,
            boolean replaceExisting) {
        String resourcePath = "defaults/pridequest/quests/" + fileName;
        try (InputStream input = loader.getResourceAsStream(resourcePath)) {
            if (input == null) {
                PrideQuest.LOGGER.warn("Missing bundled PrideQuest default resource {}", resourcePath);
                return false;
            }

            if (replaceExisting) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(input, target);
            }
            return true;
        } catch (IOException e) {
            PrideQuest.LOGGER.error("Could not install PrideQuest config {}", fileName, e);
            return false;
        }
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
