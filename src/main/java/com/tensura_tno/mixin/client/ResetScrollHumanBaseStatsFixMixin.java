package com.tensura_tno.mixin.client;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.tensura.item.misc.ResetScrollItem;
import io.github.manasmods.tensura.race.TensuraRace;
import io.github.manasmods.tensura.registry.race.TensuraRaces;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tensura's character reset flow intends to force the player into Human before
 * the reincarnation menu opens. Upstream calls resetRaceFailsafe(), but then
 * checks the storage object with an impossible instanceof and never reapplies
 * Human base aura/magicule values.
 *
 * This injects immediately after resetRaceFailsafe(player) and applies the
 * Human race's normal base existence reset. That preserves the intended
 * gameplay while removing the kill-process EP carryover window.
 */
@Mixin(ResetScrollItem.class)
public abstract class ResetScrollHumanBaseStatsFixMixin {

    @Inject(
        method = "resetEverything",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/manasmods/tensura/item/misc/ResetScrollItem;resetRaceFailsafe(Lnet/minecraft/world/entity/player/Player;)V",
            shift = At.Shift.AFTER
        ),
        require = 0,
        remap = false
    )
    private static void tensuraTno$applyHumanBaseStatsAfterFailsafe(ServerPlayer player, CallbackInfo ci) {
        ManasRace human = (ManasRace) TensuraRaces.HUMAN.get();
        if (human instanceof TensuraRace tensuraRace) {
            tensuraRace.resetExistenceData(player);
        }
    }
}
