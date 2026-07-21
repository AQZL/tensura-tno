package com.tensura_tno.compat;

import java.util.concurrent.atomic.AtomicBoolean;

import com.tensura_tno.TensuraTNOMod;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * Restores vanilla mob experience when Ancient Artifacts unexpectedly reduces a
 * valid player-kill experience drop to zero.
 *
 * <p>The compatibility rule is deliberately narrow: it is registered only when
 * {@code ancient_artifacts} is loaded, never un-cancels an event, never changes
 * environmental kills, and preserves every positive value produced by other
 * experience modifiers. Ancient Artifacts' own Tensura gear-EP death hook is
 * left untouched, so artifact growth, engraving and evolution continue to work.</p>
 */
public final class AncientArtifactsExperienceCompat {

    private static final String ANCIENT_ARTIFACTS_MOD_ID = "ancient_artifacts";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private AncientArtifactsExperienceCompat() {
    }

    public static void register() {
        if (!ModList.get().isLoaded(ANCIENT_ARTIFACTS_MOD_ID)) {
            return;
        }
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                LivingExperienceDropEvent.class,
                AncientArtifactsExperienceCompat::onExperienceDrop);
        TensuraTNOMod.LOGGER.info(
                "[TensuraTNO] Enabled Ancient Artifacts mob-experience compatibility");
    }

    private static void onExperienceDrop(LivingExperienceDropEvent event) {
        int droppedExperience = experienceAfterCompatibility(
                event.getAttackingPlayer() != null,
                event.isCanceled(),
                event.getOriginalExperience(),
                event.getDroppedExperience());

        if (droppedExperience == event.getDroppedExperience()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        TensuraTNOMod.LOGGER.debug(
                "[TensuraTNO] Restored {} experience for {} after Ancient Artifacts reduced the drop to zero",
                droppedExperience,
                entity.getType());
        event.setDroppedExperience(droppedExperience);
    }

    static int experienceAfterCompatibility(
            boolean hasAttackingPlayer,
            boolean canceled,
            int originalExperience,
            int droppedExperience) {
        if (canceled || !hasAttackingPlayer || originalExperience <= 0 || droppedExperience > 0) {
            return droppedExperience;
        }
        return originalExperience;
    }
}
