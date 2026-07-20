package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.TensuraTNOMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Replaces third-person player rendering for the Tensura lizardman race with
 * the main mod's lizardman Geo model.
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, value = Dist.CLIENT)
public final class LizardmanRacePlayerRenderHandler {

    private LizardmanRacePlayerRenderHandler() {}

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player.isSpectator() && PlayerLizardmanRenderManager.isLizardmanRace(player)) {
            event.setCanceled(true);
            return;
        }
        if (!PlayerLizardmanRenderManager.shouldRenderAsLizardman(player)) return;

        event.setCanceled(true);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            PlayerLizardmanRenderManager.render(
                    player,
                    poseStack,
                    event.getMultiBufferSource(),
                    event.getPackedLight(),
                    event.getPartialTick());
        } catch (Throwable t) {
            TensuraTNOMod.LOGGER.error(
                    "[TensuraTNO] Failed to render lizardman for player {}: {}",
                    player.getGameProfile().getName(), t.toString());
        }
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerLizardmanRenderManager.forget(event.getEntity().getUUID());
    }
}
