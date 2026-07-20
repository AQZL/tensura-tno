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
 * Replaces third-person player rendering for the Tensura orc family with the
 * regular tensura:orc Geo model.
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, value = Dist.CLIENT)
public final class OrcRacePlayerRenderHandler {

    private OrcRacePlayerRenderHandler() {}

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player.isSpectator() && PlayerOrcRenderManager.isOrcRace(player)) {
            event.setCanceled(true);
            return;
        }
        if (!PlayerOrcRenderManager.shouldRenderAsOrc(player)) return;

        event.setCanceled(true);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            PlayerOrcRenderManager.render(
                    player,
                    poseStack,
                    event.getMultiBufferSource(),
                    event.getPackedLight(),
                    event.getPartialTick());
        } catch (Throwable t) {
            TensuraTNOMod.LOGGER.error(
                    "[TensuraTNO] Failed to render orc for player {}: {}",
                    player.getGameProfile().getName(), t.toString());
        }
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerOrcRenderManager.forget(event.getEntity().getUUID());
    }
}
