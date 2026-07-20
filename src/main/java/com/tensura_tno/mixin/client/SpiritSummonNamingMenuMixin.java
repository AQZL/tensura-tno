package com.tensura_tno.mixin.client;

import com.tensura_tno.race.fox_spirit.SpiritSummonEntityHelper;
import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores the Spirit Summon identity only on Tensura's successful naming path. */
@Mixin(targets = "io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket", remap = false)
public abstract class SpiritSummonNamingMenuMixin {
    @Inject(
            method = "name",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/github/manasmods/tensura/storage/ep/IExistence;setSummoner(Ljava/util/UUID;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false,
            require = 1
    )
    private static void tno$keepSummonAfterNaming(LivingEntity summon, ServerPlayer owner,
                                                  RequestNamingMenuPacket.NamingType type, String name,
                                                  CallbackInfo ci) {
        if (owner == null || !SpiritSummonEntityHelper.isSpiritSummonOf(summon, owner)) return;
        if (TensuraStorages.getExistenceFrom(summon).getName() == null) return;
        SpiritSummonEntityHelper.restoreSpiritSummonIdentity(summon, owner);
    }
}
