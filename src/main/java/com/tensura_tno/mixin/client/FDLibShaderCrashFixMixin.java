package com.tensura_tno.mixin.client;

import net.minecraft.client.renderer.PostChain;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Prevents FDLib's post-shader initialization from crashing the game when
 * one or more shaders fail to load (e.g. under Sinytra Connector).
 *
 * FDPostShadersReloadableResourceListener.initializeShaders() collects all
 * shader load failures and then throws RuntimeException("Failed to load shaders")
 * unconditionally if ANY shader failed. We re-implement the logic here via
 * reflection (no compile-time FDLib dependency) but only log errors instead
 * of throwing. If FDLib is absent the injection silently no-ops (require = 0).
 */
@Mixin(targets = "com.finderfeed.fdlib.systems.post_shaders.FDPostShadersReloadableResourceListener", remap = false)
public class FDLibShaderCrashFixMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("tensura_tno/FDLibShaderFix");

    @Inject(
        method = "initializeShaders",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void tensuraTno$safeInitializeShaders(CallbackInfo ci) {
        try {
            // --- 1. Close & clear existing post-shaders ---
            Class<?> handlerClass = Class.forName(
                "com.finderfeed.fdlib.systems.post_shaders.FDPostShadersHandler");
            @SuppressWarnings("unchecked")
            List<PostChain> postShaders = (List<PostChain>) handlerClass.getField("POST_SHADERS").get(null);
            for (PostChain shader : new ArrayList<>(postShaders)) {
                try { shader.close(); } catch (Exception ignored) {}
            }
            postShaders.clear();

            // --- 2. Fire the NeoForge event so mods can register their shaders ---
            Class<?> eventClass = Class.forName(
                "com.finderfeed.fdlib.systems.post_shaders.FDPostShaderInitializeEvent");
            Event event = (Event) eventClass.getDeclaredConstructor().newInstance();
            NeoForge.EVENT_BUS.post(event);

            // --- 3. Load each registered shader, swallowing individual failures ---
            Method getRegistryMethod = eventClass.getMethod("getPostChainRegistry");
            @SuppressWarnings("unchecked")
            List<Object> registry = (List<Object>) getRegistryMethod.invoke(event);

            Class<?> listenerClass = Class.forName(
                "com.finderfeed.fdlib.systems.post_shaders.FDPostShadersReloadableResourceListener");
            Class<?> shaderLoadInstanceClass = Class.forName(
                "com.finderfeed.fdlib.systems.post_shaders.FDPostShaderInitializeEvent$PostChainShaderLoadInstance");
            Method loadPostChainMethod = listenerClass.getMethod("loadPostChain", shaderLoadInstanceClass);

            List<Throwable> errors = new ArrayList<>();
            for (Object shader : registry) {
                try {
                    loadPostChainMethod.invoke(null, shader);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    errors.add(cause);
                }
            }

            // --- 4. Log failures instead of throwing ---
            if (!errors.isEmpty()) {
                LOGGER.error("[tensura_tno] {} FDLib post-shader(s) failed to load. Suppressing crash:", errors.size());
                for (Throwable e : errors) {
                    LOGGER.error("  Shader load error:", e);
                }
                LOGGER.warn("[tensura_tno] Game will continue without the failed shaders.");
            } else {
                LOGGER.info("[tensura_tno] FDLib shaders initialized successfully (safe wrapper).");
            }

        } catch (Exception e) {
            LOGGER.error("[tensura_tno] FDLib shader init safe-wrapper encountered an unexpected error:", e);
        }
        ci.cancel();
    }
}
