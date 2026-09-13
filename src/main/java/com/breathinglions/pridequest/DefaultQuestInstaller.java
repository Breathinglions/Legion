package com.breathinglions.pridequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Seeds a usable starter quest pack on first boot without overwriting an
 * administrator's edited configs.
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
        int created = 0;

        for (String fileName : DEFAULT_FILES) {
            Path target = questDir.resolve(fileName);
            if (Files.exists(target)) continue;

            String resourcePath = "defaults/pridequest/quests/" + fileName;
            try (InputStream input = loader.getResourceAsStream(resourcePath)) {
                if (input == null) {
                    PrideQuest.LOGGER.warn("Missing bundled PrideQuest default resource {}", resourcePath);
                    continue;
                }

                Files.copy(input, target);
                created++;
                PrideQuest.LOGGER.info("Seeded PrideQuest config {}", target.getFileName());
            } catch (IOException e) {
                PrideQuest.LOGGER.error("Could not seed PrideQuest config {}", fileName, e);
            }
        }

        if (created > 0) {
            PrideQuest.LOGGER.info("Seeded {} PrideQuest default quest config(s)", created);
        }
    }
}
