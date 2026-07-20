package com.tensura_tno.food;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tensura_tno.TensuraTNOMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-food EP/MP/AP configuration stored in config/tno/<item_id>.json.
 *
 * File naming: namespace:path → "namespace_path.json"
 * e.g. minecraft:cooked_beef → "minecraft_cooked_beef.json"
 */
public final class FoodEPManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_EP_PER_NUTRITION = 5;

    private static final Map<String, FoodEPConfig> CACHE = new ConcurrentHashMap<>();
    /** Items confirmed to have no JSON config — avoids repeated disk reads. */
    private static final java.util.Set<String> NO_CONFIG = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static Path configDir;

    private FoodEPManager() {}

    public static void init() {
        configDir = FMLPaths.CONFIGDIR.get().resolve("tno");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            TensuraTNOMod.LOGGER.error("[FoodEP] Failed to create config/tno directory", e);
        }
    }

    // ── Key helpers ──────────────────────────────────────────────────────────

    public static String keyFor(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    private static Path fileFor(Item item) {
        return configDir.resolve(keyFor(item) + ".json");
    }

    // ── Load / save ──────────────────────────────────────────────────────────

    public static @Nullable FoodEPConfig getConfig(Item item) {
        String key = keyFor(item);
        FoodEPConfig cached = CACHE.get(key);
        if (cached != null) return cached;
        if (NO_CONFIG.contains(key)) return null;

        Path file = fileFor(item);
        if (!Files.exists(file)) {
            NO_CONFIG.add(key);
            return null;
        }
        try (Reader r = Files.newBufferedReader(file)) {
            FoodEPConfig cfg = GSON.fromJson(r, FoodEPConfig.class);
            if (cfg != null) {
                CACHE.put(key, cfg);
            } else {
                NO_CONFIG.add(key);
            }
            return cfg;
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            TensuraTNOMod.LOGGER.error("[FoodEP] Failed to read {}", file, e);
            NO_CONFIG.add(key);
            return null;
        }
    }

    public static void setConfig(Item item, @Nullable String ep,
                                 @Nullable String mp, @Nullable String ap) {
        FoodEPConfig cfg = new FoodEPConfig(ep, mp, ap);
        String key = keyFor(item);
        CACHE.put(key, cfg);

        if (configDir == null) return;
        try (Writer w = Files.newBufferedWriter(fileFor(item))) {
            GSON.toJson(cfg, w);
        } catch (IOException e) {
            TensuraTNOMod.LOGGER.error("[FoodEP] Failed to save config for {}", key, e);
        }
    }

    public static boolean hasCustomConfig(Item item) {
        return getConfig(item) != null;
    }

    /** Invalidate cache for one item (after writing). */
    public static void invalidate(Item item) {
        String key = keyFor(item);
        CACHE.remove(key);
        NO_CONFIG.remove(key);
    }

    // ── Value resolution ─────────────────────────────────────────────────────

    /**
     * Returns the EP amount to add to the player's max EP when they eat this food.
     * Falls back to nutrition × DEFAULT_EP_PER_NUTRITION if no custom config.
     */
    public static double resolveEP(ItemStack stack, LivingEntity entity) {
        FoodEPConfig cfg = getConfig(stack.getItem());
        if (cfg != null && cfg.getEp() != null) {
            double maxEP = io.github.manasmods.tensura.util.EnergyHelper.getMaxEP(entity);
            return FoodEPConfig.resolve(cfg.getEp(), maxEP);
        }
        FoodProperties food = stack.getFoodProperties(entity);
        if (food == null) return 0;
        return (double) food.nutrition() * DEFAULT_EP_PER_NUTRITION;
    }

    /** Returns MP to restore (0 if not configured). */
    public static double resolveMP(ItemStack stack, LivingEntity entity) {
        FoodEPConfig cfg = getConfig(stack.getItem());
        if (cfg == null || cfg.getMp() == null) return 0;
        double maxMP = io.github.manasmods.tensura.util.EnergyHelper.getMaxMagicule(entity);
        return FoodEPConfig.resolve(cfg.getMp(), maxMP);
    }

    /** Returns AP to restore (0 if not configured). */
    public static double resolveAP(ItemStack stack, LivingEntity entity) {
        FoodEPConfig cfg = getConfig(stack.getItem());
        if (cfg == null || cfg.getAp() == null) return 0;
        double maxAP = io.github.manasmods.tensura.util.EnergyHelper.getMaxAura(entity);
        return FoodEPConfig.resolve(cfg.getAp(), maxAP);
    }

    /**
     * Returns the tooltip EP display string for an item, e.g. "100" or "1.5%".
     * Returns null if the item is not a food.
     */
    public static @Nullable String getTooltipEP(ItemStack stack) {
        FoodEPConfig cfg = getConfig(stack.getItem());
        if (cfg != null && cfg.getEp() != null) return cfg.getEp();
        FoodProperties food = stack.getFoodProperties(null);
        if (food == null) return null;
        return String.valueOf(food.nutrition() * DEFAULT_EP_PER_NUTRITION);
    }

    public static @Nullable String getTooltipMP(ItemStack stack) {
        FoodEPConfig cfg = getConfig(stack.getItem());
        return (cfg != null) ? cfg.getMp() : null;
    }

    public static @Nullable String getTooltipAP(ItemStack stack) {
        FoodEPConfig cfg = getConfig(stack.getItem());
        return (cfg != null) ? cfg.getAp() : null;
    }
}
