package com.tensura_tno.race.fox_spirit;

import dev.architectury.event.EventResult;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;

/**
 * Makes Spirit Summon entities use Tensura's naming UI without receiving
 * naming EP, evolving, or charging the owner for an EP reward they do not get.
 */
public final class SpiritSummonNamingHandler {
    private static boolean registered;

    private SpiritSummonNamingHandler() {
    }

    public static synchronized void register() {
        if (registered) return;
        TensuraEntityEvents.NAMING_EVENT.register((entity, namer, epGain, cost, namingType, name) -> {
            if (namer == null || !SpiritSummonEntityHelper.isSpiritSummonOf(entity, namer)) {
                return EventResult.pass();
            }

            // Naming is only an ownership/skill-copy bridge for Spirit Summons.
            // It must not increase EP, evolve the summon, or consume the namer's EP.
            epGain.set(0.0D);
            cost.set(0.0D);
            namingType.set(RequestNamingMenuPacket.NamingType.LOW);
            return EventResult.pass();
        });
        registered = true;
    }
}
