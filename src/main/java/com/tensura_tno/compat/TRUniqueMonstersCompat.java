package com.tensura_tno.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Defers {@code net.crypticmc.tr_unique_monsters.registry.skill.ExtraSkills.init()} to
 * {@code RegisterEvent} (HIGHEST priority), after {@code NewRegistryEvent.fill()} has
 * built all custom registries, but before any {@code DeferredRegister} baking runs.
 *
 * <p>The tr_unique_monsters mod injects into Tensura's {@code init()} via a Mixin, which runs
 * during parallel mod construction — before ManasCore's skill registry has published itself.
 * {@link ExtraSkillsInitFixMixin} cancels the premature call; this class replays it at the
 * correct time so {@code SKILLS.register()} subscribes on the event bus before
 * {@code RegisterEvent} bakes the DeferredRegister entries.</p>
 *
 * <p><b>Why not FMLCommonSetupEvent?</b> By CommonSetup, {@code RegisterEvent} for every
 * registry has already fired. Calling {@code SKILLS.register()} that late means the
 * DeferredRegister listener is added to the bus but the event never fires again, leaving
 * {@code APPRAISAL_EYE.get()} returning {@code null} and crashing in MasteryHandler.</p>
 */
public class TRUniqueMonstersCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Set to {@code true} by this class before invoking {@code ExtraSkills.init()} so the
     *  Mixin guard allows the call through. */
    public static volatile boolean allowInit = false;

    @ApiStatus.Internal
    public static void triggerDeferredInit() {
        if (!ModList.get().isLoaded("tr_unique_monsters")) {
            return;
        }
        try {
            allowInit = true;
            Class<?> extraSkillsClass = Class.forName(
                "net.crypticmc.tr_unique_monsters.registry.skill.ExtraSkills"
            );
            Method initMethod = extraSkillsClass.getMethod("init");
            initMethod.invoke(null);
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.error("[tensura_tno] Failed to defer ExtraSkills.init() — tr_unique_monsters " +
                "features may be unavailable. This is a non-fatal compat error.", e);
        } finally {
            allowInit = false;
        }
    }
}
