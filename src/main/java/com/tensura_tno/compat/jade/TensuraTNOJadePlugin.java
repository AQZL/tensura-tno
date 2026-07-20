package com.tensura_tno.compat.jade;

import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("jade")
public final class TensuraTNOJadePlugin implements IWailaPlugin {
    private static boolean commonRegistered;
    private static boolean clientRegistered;

    @Override
    public void register(IWailaCommonRegistration registration) {
        if (commonRegistered) {
            return;
        }
        commonRegistered = true;
        registration.registerEntityDataProvider(TensuraEntitySkillProvider.INSTANCE, LivingEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        if (clientRegistered) {
            return;
        }
        clientRegistered = true;
        registration.addConfig(TensuraEntitySkillProvider.UID, true);
        registration.registerEntityComponent(TensuraEntitySkillProvider.INSTANCE, LivingEntity.class);
    }
}
