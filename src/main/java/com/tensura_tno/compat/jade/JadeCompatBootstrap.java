package com.tensura_tno.compat.jade;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

public final class JadeCompatBootstrap {
    private JadeCompatBootstrap() {
    }

    public static void registerIfLoaded() {
        if (!ModList.get().isLoaded("jade")) {
            return;
        }

        try {
            Class<?> pluginClass = Class.forName("com.tensura_tno.compat.jade.TensuraTNOJadePlugin");
            Object plugin = pluginClass.getDeclaredConstructor().newInstance();

            Class<?> commonRegistrationClass = Class.forName("snownee.jade.impl.WailaCommonRegistration");
            Object commonRegistration = commonRegistrationClass.getMethod("instance").invoke(null);
            Class<?> commonApiClass = Class.forName("snownee.jade.api.IWailaCommonRegistration");
            pluginClass.getMethod("register", commonApiClass).invoke(plugin, commonRegistration);

            if (FMLEnvironment.dist.isClient()) {
                Class<?> clientRegistrationClass = Class.forName("snownee.jade.impl.WailaClientRegistration");
                Object clientRegistration = clientRegistrationClass.getMethod("instance").invoke(null);
                Class<?> clientApiClass = Class.forName("snownee.jade.api.IWailaClientRegistration");
                pluginClass.getMethod("registerClient", clientApiClass).invoke(plugin, clientRegistration);
            }
        } catch (Throwable ignored) {
        }
    }
}
