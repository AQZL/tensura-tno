package com.tensura_tno.mixin.client;

import com.tensura_tno.compat.stextras.SkillLockEligibility;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.crypticdev.stextras.client.screen.skilllock.STSkillLockFilters", remap = false)
public class SkillLockResistanceFilterMixin {
    @Inject(method = "collectAvailableAbilities", at = @At("RETURN"), cancellable = true, remap = false)
    private static void tno$hideUnlockedResistanceSkills(Player player, CallbackInfoReturnable<List<ManasSkillInstance>> cir) {
        List<ManasSkillInstance> abilities = cir.getReturnValue();
        if (abilities == null || abilities.isEmpty()) {
            return;
        }

        List<ManasSkillInstance> filtered = new ArrayList<>(abilities);
        filtered.removeIf(SkillLockResistanceFilterMixin::tno$shouldHideFromSkillLockList);
        cir.setReturnValue(filtered);
    }

    private static boolean tno$shouldHideFromSkillLockList(ManasSkillInstance instance) {
        if (instance == null || instance.getSkill() == null) {
            return false;
        }

        ResourceLocation id = SkillAPI.getSkillRegistry().getId(instance.getSkill());
        return SkillLockEligibility.isResistanceSkill(id) && !tno$isLockedOrAdminLocked(id);
    }

    private static boolean tno$isLockedOrAdminLocked(ResourceLocation id) {
        if (id == null) {
            return false;
        }

        try {
            Class<?> playerStorage = Class.forName("org.crypticdev.stextras.storage.STExtarsStorage$Player");
            Method isSkillLocked = playerStorage.getMethod("isSkillLocked", ResourceLocation.class);
            Method isSkillAdminLocked = playerStorage.getMethod("isSkillAdminLocked", ResourceLocation.class);
            return Boolean.TRUE.equals(isSkillLocked.invoke(null, id))
                    || Boolean.TRUE.equals(isSkillAdminLocked.invoke(null, id));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
