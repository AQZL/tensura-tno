package com.tensura_tno.config;

import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.tensura.config.ReincarnationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Injects Tensura TNO race entries into Tensura's ReincarnationConfig.
 * The injection runs only when TNOGeneralConfig.General.refresh is true.
 */
public class TNOConfigInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger("TensuraTNO/ConfigInjector");

    private static final List<String> STARTING_RACES = List.of(
            "tensura_tno:baby_spirit_fox"
    );

    private static final List<String> RANDOM_RACES = List.of(
            "tensura_tno:baby_spirit_fox"
    );

    public static void addToConfig() {
        try {
            TNOGeneralConfig config = ConfigRegistry.getConfig(TNOGeneralConfig.class);
            if (config == null) {
                LOGGER.warn("TNOGeneralConfig not registered, skipping race injection");
                return;
            }
            if (!config.General.refresh) {
                LOGGER.info("[TensuraTNO] Race injection refresh is disabled, skipping");
                return;
            }

            ReincarnationConfig reinc = (ReincarnationConfig) ConfigRegistry.getConfig(ReincarnationConfig.class);
            if (reinc == null || reinc.Races == null) {
                LOGGER.warn("ReincarnationConfig not found, skipping race injection");
                return;
            }

            boolean changesMade = false;

            LinkedHashSet<String> startRaceSet = new LinkedHashSet<>(
                    reinc.Races.startingRaces != null ? reinc.Races.startingRaces : List.of());
            for (String race : STARTING_RACES) {
                if (startRaceSet.add(race)) {
                    changesMade = true;
                    LOGGER.info("Added {} to startingRaces", race);
                }
            }

            LinkedHashSet<String> randomRaceSet = new LinkedHashSet<>(
                    reinc.Races.randomRaces != null ? reinc.Races.randomRaces : List.of());
            for (String race : RANDOM_RACES) {
                if (randomRaceSet.add(race)) {
                    changesMade = true;
                    LOGGER.info("Added {} to randomRaces", race);
                }
            }

            LinkedHashSet<String> reincarnationRaceSet = new LinkedHashSet<>(
                    reinc.Races.reincarnationRaces != null ? reinc.Races.reincarnationRaces : List.of());
            for (String race : STARTING_RACES) {
                if (reincarnationRaceSet.add(race)) {
                    changesMade = true;
                    LOGGER.info("Added {} to reincarnationRaces", race);
                }
            }

            LinkedHashSet<String> masteredSet = new LinkedHashSet<>(
                    reinc.Races.reincarnationRacesMastered != null ? reinc.Races.reincarnationRacesMastered : List.of());
            for (String race : STARTING_RACES) {
                if (masteredSet.add(race)) {
                    changesMade = true;
                    LOGGER.info("Added {} to reincarnationRacesMastered", race);
                }
            }

            if (changesMade) {
                reinc.Races.startingRaces = new ArrayList<>(startRaceSet);
                reinc.Races.randomRaces = new ArrayList<>(randomRaceSet);
                reinc.Races.reincarnationRaces = new ArrayList<>(reincarnationRaceSet);
                reinc.Races.reincarnationRacesMastered = new ArrayList<>(masteredSet);
                LOGGER.info("[TensuraTNO] Race injection entries added");
            } else {
                LOGGER.info("[TensuraTNO] All races already present in config, no changes needed");
            }

            config.General.refresh = false;
            ConfigRegistry.saveAllConfigs();
            LOGGER.info("[TensuraTNO] Race injection refresh disabled, config saved");
        } catch (Exception e) {
            LOGGER.error("[TensuraTNO] Failed to inject races into ReincarnationConfig", e);
        }
    }
}
