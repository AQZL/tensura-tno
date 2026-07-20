package com.tensura_tno.client.race;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensura_tno.TensuraTNOMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Replaces third-person rendering for the complete fox-spirit race line. */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, value = Dist.CLIENT)
public final class FoxSpiritRacePlayerRenderHandler {

    private FoxSpiritRacePlayerRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player.isSpectator() && PlayerFoxSpiritRenderManager.isFoxSpiritRace(player)) {
            event.setCanceled(true);
            return;
        }
        if (!PlayerFoxSpiritRenderManager.shouldRenderAsFoxSpirit(player)) return;

        event.setCanceled(true);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            PlayerFoxSpiritRenderManager.render(
                    player,
                    poseStack,
                    event.getMultiBufferSource(),
                    event.getPackedLight(),
                    event.getPartialTick());
        } catch (Throwable throwable) {
            TensuraTNOMod.LOGGER.error(
                    "[TensuraTNO] Failed to render fox-spirit model for player {}: {}",
                    player.getGameProfile().getName(), throwable.toString());
        } finally {
            poseStack.popPose();
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerFoxSpiritRenderManager.forget(event.getEntity().getUUID());
    }
}
