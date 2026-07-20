package com.tensura_tno.mixin.client;

import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.github.manasmods.tensura.menu.ReincarnationMenu", remap = false)
public class ChosenOneReincarnationBlessingMixin {

    @Inject(method = "setRace", at = @At("TAIL"), remap = false)
    private static void tno$restoreChosenOneBlessing(Player player, @Coerce Object race, boolean resetEP, boolean grantUnique, CallbackInfo ci) {
        if (!player.level().isClientSide() && SkillUtils.hasSkill(player, UniqueSkills.CHOSEN_ONE.get())) {
            IExistence existence = TensuraStorages.getExistenceFrom(player);
            existence.setBlessed(true);
            existence.markDirty();
        }
    }
}
