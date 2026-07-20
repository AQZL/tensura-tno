package com.tensura_tno.client;

import com.tensura_tno.TensuraTNOMod;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, value = Dist.CLIENT)
public final class MagicSenseShaderNightVisionCompat {
    private static final int COMPAT_NIGHT_VISION_TICKS = 260;

    private static Method irisApiGetInstance;
    private static Method irisApiIsShaderPackInUse;
    private static Method irisQuickShaderCheck;
    private static boolean reflectionInitialized;
    private static boolean reflectionUnavailable;
    private static boolean appliedCompatNightVision;

    private MagicSenseShaderNightVisionCompat() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            appliedCompatNightVision = false;
            return;
        }

        boolean shouldApply = isShaderPackInUse() && hasTensuraVision(player);

        if (shouldApply) {
            applyCompatNightVision(player);
        } else {
            removeCompatNightVision(player);
        }
    }

    private static void applyCompatNightVision(LocalPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && !isCompatNightVision(current)) {
            appliedCompatNightVision = false;
            return;
        }

        if (current == null || current.getDuration() < COMPAT_NIGHT_VISION_TICKS - 20) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION,
                    COMPAT_NIGHT_VISION_TICKS,
                    0,
                    false,
                    false,
                    false));
        }
        appliedCompatNightVision = true;
    }

    private static boolean hasTensuraVision(LocalPlayer player) {
        return player.getAttributeValue(TensuraAttributes.PRESENCE_SENSE) > 0.0D
                || player.getAttributeValue(TensuraAttributes.DARK_VISION) > 0.0D;
    }

    private static void removeCompatNightVision(LocalPlayer player) {
        if (!appliedCompatNightVision) return;
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && isCompatNightVision(current)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        appliedCompatNightVision = false;
    }

    private static boolean isCompatNightVision(MobEffectInstance instance) {
        return instance.getAmplifier() == 0
                && !instance.isAmbient()
                && !instance.isVisible()
                && !instance.showIcon()
                && instance.getDuration() <= COMPAT_NIGHT_VISION_TICKS;
    }

    private static boolean isShaderPackInUse() {
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) {
            return false;
        }

        try {
            ensureShaderReflection();
            if (irisApiGetInstance != null && irisApiIsShaderPackInUse != null) {
                Object irisApi = irisApiGetInstance.invoke(null);
                return Boolean.TRUE.equals(irisApiIsShaderPackInUse.invoke(irisApi));
            }
            if (irisQuickShaderCheck != null) {
                return Boolean.TRUE.equals(irisQuickShaderCheck.invoke(null));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!reflectionUnavailable) {
                reflectionUnavailable = true;
                TensuraTNOMod.LOGGER.warn(
                        "[TensuraTNO] Magic Sense shader night vision compat disabled: Iris/Oculus reflection failed",
                        e);
            }
        }

        return false;
    }

    private static void ensureShaderReflection() throws ReflectiveOperationException {
        if (reflectionInitialized || reflectionUnavailable) return;
        reflectionInitialized = true;

        if (tryInitIrisApi()) return;
        if (tryInitQuickShaderCheck("net.irisshaders.iris.Iris")) return;
        if (tryInitQuickShaderCheck("net.coderbot.iris.Iris")) return;

        reflectionUnavailable = true;
    }

    private static boolean tryInitIrisApi() {
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApiGetInstance = apiClass.getMethod("getInstance");
            irisApiIsShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean tryInitQuickShaderCheck(String className) {
        try {
            Class<?> irisClass = Class.forName(className);
            irisQuickShaderCheck = irisClass.getMethod("isPackInUseQuick");
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
