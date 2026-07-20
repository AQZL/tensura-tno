package com.tensura_tno.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Defers {@code com.github.dominickwd04.traddon.TrAddon.init()} to
 * {@code RegisterEvent} (HIGHEST priority), after {@code NewRegistryEvent.fill()} has
 * built all custom registries (including {@code manascore_skill:skills} and
 * {@code manascore_race:races}), but before any {@code DeferredRegister} baking runs.
 *
 * <p>traddon's {@code TrAddonNeoForge} constructor calls {@code TrAddon.init()} immediately,
 * which triggers registrations into custom ManasCore registries via Architectury
 * {@code DeferredRegister}. Those registries do not yet exist during parallel mod construction,
 * so the call must be deferred.</p>
 *
 * <p>{@link TrAddonInitFixMixin} cancels the premature call; this class replays it at the
 * correct time so the DeferredRegister subscriptions land before their RegisterEvents fire.</p>
 */
public class TrAddonCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Set to {@code true} by this class before invoking {@code TrAddon.init()} so the
     *  Mixin guard allows the call through. */
    public static volatile boolean allowInit = false;

    @ApiStatus.Internal
    public static void triggerDeferredInit() {
        if (!ModList.get().isLoaded("traddon")) {
            return;
        }
        try {
            allowInit = true;
            Class<?> trAddonClass = Class.forName(
                "com.github.dominickwd04.traddon.TrAddon"
            );
            Method initMethod = trAddonClass.getMethod("init");
            initMethod.invoke(null);
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.error("[tensura_tno] Failed to defer TrAddon.init() — traddon features " +
                "may be unavailable. This is a non-fatal compat error.", e);
        } finally {
            allowInit = false;
        }
    }
}
