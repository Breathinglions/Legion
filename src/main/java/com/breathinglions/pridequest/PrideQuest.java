package com.breathinglions.pridequest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrideQuest implements ModInitializer {
    public static final String MOD_ID = "pridequest";
    public static final String VERSION = "0.2.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final QuestRepository REPO = new QuestRepository();
    public static final QuestHud HUD = new QuestHud();
    public static final QuestService SERVICE = new QuestService(REPO, HUD);

    @Override
    public void onInitialize() {
        DefaultQuestInstaller.seed(REPO.questDir());
        REPO.loadQuests();
        PrideQuestCommands.register(REPO, SERVICE, HUD);
        CobblemonHooks.register(SERVICE);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            REPO.attachServer(server);
            LOGGER.info("PrideQuest attached to world {}", server.getWorldData().getLevelName());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                REPO.saveAll(server.getPlayerList().getPlayers()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SERVICE.tick(server.getPlayerList().getPlayers());
            HUD.tick(server.getPlayerList().getPlayers(), REPO);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                SERVICE.refresh(handler.player));

        LOGGER.info(
                "PrideQuest v{} initialized - server-side multi-quest tracking enabled",
                VERSION);
    }
}
