package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;

/** Applies prone dimensions to the sleeping pose, which bypasses Player#getDefaultDimensions. */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID)
public final class FoxSpiritPlayerSizeHandler {

    private FoxSpiritPlayerSizeHandler() {
    }

    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getPose() != Pose.SLEEPING) return;
        if (!FoxSpiritPlayerFormHelper.shouldUseFoxForm(player)) return;
        event.setNewSize(player.getDimensions(Pose.SWIMMING));
    }
}
