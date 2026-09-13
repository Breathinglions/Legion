package com.breathinglions.pridequest;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

public final class CobblemonHooks {
    private CobblemonHooks() {}

    public static void register(QuestService service) {
        CobblemonEvents.STARTER_CHOSEN.subscribe(Priority.NORMAL, event -> {
            ServerPlayer player = event.getPlayer();
            service.event(player, "starter_chosen");
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            ServerPlayer player = event.getPlayer();
            service.event(player, "catch_pokemon");
            return Unit.INSTANCE;
        });

        CobblemonEvents.APRICORN_HARVESTED.subscribe(Priority.NORMAL, event -> {
            ServerPlayer player = event.getPlayer();
            service.event(player, "harvest_apricorn");
            return Unit.INSTANCE;
        });
    }
}
