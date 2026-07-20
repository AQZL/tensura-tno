package com.tensura_tno.util;

/**
 * Thread-local guards for the prestige soul-grade system.
 *
 * <p>BLOCKED: set to true during ResetScrollItem.isFullReset() to prevent
 * STExtras' early doPrestige() call from granting soul grade before the
 * character is actually reset. This fixes the exploit where using a skill
 * reset scroll grants soul grade repeatedly without a full character reset.
 *
 * <p>IN_PROGRESS: set to true only during actual doPrestige() calls that
 * are NOT blocked, so RacePrestigeSoulGradeMixin knows when to apply.
 */
public final class PrestigeGuard {
    public static final ThreadLocal<Boolean> IN_PROGRESS = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> BLOCKED = ThreadLocal.withInitial(() -> false);
    private PrestigeGuard() {}
}
