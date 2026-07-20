package com.tensura_tno;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TensuraTNOCompatConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    private static final ModConfigSpec.BooleanValue NPC_WORKING_DEFAULT_DISABLED;
    private static final ModConfigSpec.BooleanValue SOPHISTICATED_BACKPACKS_EVOLUTION_SLOT_REFRESH;
    private static final ModConfigSpec.BooleanValue VANILLA_BACKPORT_DELAYED_SHEEP_LAYER;
    private static final ModConfigSpec.BooleanValue CURIOS_SLOT_ATTRIBUTE_CONCURRENCY;
    private static final ModConfigSpec.IntValue SOUL_GRADE_PER_PRESTIGE;
    private static final ModConfigSpec.BooleanValue MOWZIE_AVOID_GOAL_OPT;
    private static final ModConfigSpec.BooleanValue RIMURU_MODE_CHECKBOX_ENABLED;

    static {
        ModConfigSpec.Builder commonBuilder = new ModConfigSpec.Builder();

        commonBuilder.push("compatibility_patches");

        NPC_WORKING_DEFAULT_DISABLED = commonBuilder
            .comment("Change the default value of the npcWorking game rule from true to false.")
            .define("npcWorkingDefaultDisabled", true);

        SOPHISTICATED_BACKPACKS_EVOLUTION_SLOT_REFRESH = commonBuilder
            .comment("Refresh Sophisticated Backpacks slot components after Tensura gear evolution.")
            .define("sophisticatedBackpacksEvolutionSlotRefresh", true);

        SOUL_GRADE_PER_PRESTIGE = commonBuilder
            .comment("Amount of Soul Grade gained per prestige (reincarnation) when no race prestige quest is completed.\n" +
                     "Default: 1. Set to 0 to disable automatic gain. Higher values speed up soul grade progression.")
            .defineInRange("soulGradePerPrestige", 1, 0, 100);

        commonBuilder.pop();

        commonBuilder.push("performance");

        MOWZIE_AVOID_GOAL_OPT = commonBuilder
            .comment("Optimize Mowzie's Mobs AvoidEntityIfNotTamedGoal to skip expensive entity scans\n" +
                     "for tamed animals and throttle scan frequency for untamed ones (every 4 ticks).\n" +
                     "Eliminates ~2-5% server tick cost per BladeTiger / Tensura tamable entity.")
            .define("mowzieAvoidGoalOptimization", true);

        commonBuilder.pop();

        commonBuilder.push("gameplay");

        RIMURU_MODE_CHECKBOX_ENABLED = commonBuilder
            .comment("Allow players to tick a per-player Rimuru-mode checkbox in the reincarnation GUI\n" +
                     "when picking the Slime race for the first time. Default: false.\n" +
                     "Note: this option only gates DEDICATED servers. Single-player worlds and integrated\n" +
                     "servers (including LAN hosts) ignore it and the checkbox is always available there.\n" +
                     "When true on a dedicated server, the checkbox shows up only on first race selection\n" +
                     "(not on race-reset / character-reset scrolls) and only if the world's vanilla\n" +
                     "'rimuruMode' game rule is OFF.")
            .define("rimuruModeCheckboxEnabled", false);

        commonBuilder.pop();

        COMMON_SPEC = commonBuilder.build();

        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("compatibility_patches");

        VANILLA_BACKPORT_DELAYED_SHEEP_LAYER = builder
            .comment("Delay VanillaBackport sheep undercoat layer registration until its config is ready.")
            .define("vanillaBackportDelayedSheepLayer", true);

        CURIOS_SLOT_ATTRIBUTE_CONCURRENCY = builder
            .comment("Make Curios SlotAttribute.getOrCreate thread-safe to avoid creative tooltip ConcurrentModificationException.")
            .define("curiosSlotAttributeConcurrency", true);

        builder.pop();

        CLIENT_SPEC = builder.build();
    }

    private TensuraTNOCompatConfig() {
    }

    public static boolean isNpcWorkingDefaultDisabled() {
        return getOrDefault(NPC_WORKING_DEFAULT_DISABLED, true);
    }

    public static boolean isSophisticatedBackpacksEvolutionSlotRefreshEnabled() {
        return getOrDefault(SOPHISTICATED_BACKPACKS_EVOLUTION_SLOT_REFRESH, true);
    }

    public static boolean isVanillaBackportDelayedSheepLayerEnabled() {
        return getOrDefault(VANILLA_BACKPORT_DELAYED_SHEEP_LAYER, true);
    }

    public static boolean isCuriosSlotAttributeConcurrencyEnabled() {
        return getOrDefault(CURIOS_SLOT_ATTRIBUTE_CONCURRENCY, true);
    }

    public static boolean isMowzieAvoidGoalOptEnabled() {
        return getOrDefault(MOWZIE_AVOID_GOAL_OPT, true);
    }

    public static boolean isRimuruModeCheckboxEnabled() {
        return getOrDefault(RIMURU_MODE_CHECKBOX_ENABLED, false);
    }

    public static int getSoulGradePerPrestige() {
        try {
            return SOUL_GRADE_PER_PRESTIGE.get();
        } catch (IllegalStateException ignored) {
            return 1;
        }
    }

    private static boolean getOrDefault(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }
}
