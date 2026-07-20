package com.tensura_tno.client.browser;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class TensuraScreenLayout {

    private TensuraScreenLayout() {
    }

    public static int readIntField(Object instance, String name) {
        if (instance == null) {
            return 0;
        }

        Field field = findField(instance.getClass(), name);
        if (field == null) {
            return 0;
        }

        try {
            field.setAccessible(true);
            if (Modifier.isStatic(field.getModifiers())) {
                return field.getInt(null);
            }
            return field.getInt(instance);
        } catch (IllegalAccessException ignored) {
            return 0;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }
}
