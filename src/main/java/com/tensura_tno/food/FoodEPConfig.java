package com.tensura_tno.food;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * Per-food EP/MP/AP configuration loaded from config/tno/<item_id>.json.
 *
 * Values may be:
 *   - A plain number: "100"  → add 100 flat
 *   - A percentage:  "1.5%" → add 1.5% of the player's current max
 */
public class FoodEPConfig {

    @SerializedName("ep")
    private @Nullable String ep;

    @SerializedName("mp")
    private @Nullable String mp;

    @SerializedName("ap")
    private @Nullable String ap;

    public FoodEPConfig(@Nullable String ep, @Nullable String mp, @Nullable String ap) {
        this.ep = ep;
        this.mp = mp;
        this.ap = ap;
    }

    public @Nullable String getEp() { return ep; }
    public @Nullable String getMp() { return mp; }
    public @Nullable String getAp() { return ap; }

    /** Returns true if this string represents a percentage (ends with '%'). */
    public static boolean isPercent(String value) {
        return value != null && value.trim().endsWith("%");
    }

    /**
     * Resolves a value string to a concrete double given the relevant maximum.
     *
     * @param value   the raw string ("100" or "1.5%")
     * @param maxRef  the reference maximum used for percentage calculation
     * @return        the resolved amount, or 0 if value is null/blank/invalid
     */
    public static double resolve(@Nullable String value, double maxRef) {
        if (value == null || value.isBlank()) return 0;
        value = value.trim();
        try {
            if (isPercent(value)) {
                double pct = Double.parseDouble(value.substring(0, value.length() - 1));
                return maxRef * (pct / 100.0);
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
