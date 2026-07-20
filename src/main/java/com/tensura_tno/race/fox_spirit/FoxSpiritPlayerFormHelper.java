package com.tensura_tno.race.fox_spirit;

import com.tensura_tno.ability.skill.HumanFormSkill;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Shared fox-spirit form detection for common and client-only systems. */
public final class FoxSpiritPlayerFormHelper {

    private FoxSpiritPlayerFormHelper() {
    }

    public static boolean shouldUseFoxForm(Player player) {
        if (player == null || player.isSpectator()) return false;
        try {
            if (!FoxSpiritSummonBonus.isFoxSpiritRace(player)) return false;
            AttributeInstance scale = player.getAttribute(Attributes.SCALE);
            return scale == null || scale.getModifier(HumanFormSkill.HUMAN_FORM_SCALE) == null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
