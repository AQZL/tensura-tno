package com.tensura_tno.util;

public final class PonderEditorAccess {
    private static volatile boolean enabled = false;

    private PonderEditorAccess() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
