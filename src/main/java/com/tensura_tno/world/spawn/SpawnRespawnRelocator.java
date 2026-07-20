package com.tensura_tno.world.spawn;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Public respawn listener bridge for the spawn-biome blacklist feature.
 */
public final class SpawnRespawnRelocator {
    private SpawnRespawnRelocator() {
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        SpawnRelocator.onPlayerRespawn(event);
    }
}
