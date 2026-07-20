package com.tensura_tno.client;

import com.tensura_tno.TensuraTNOCompatConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class VanillaBackportPendingLayers {

    private static final List<PendingLayerRegistration> pendingRegistrations = new ArrayList<>();

    private VanillaBackportPendingLayers() {
    }

    public static void enqueue(Supplier<?> supplier, Consumer<Object> consumer) {
        pendingRegistrations.add(new PendingLayerRegistration(supplier, consumer));
    }

    public static void flush() {
        if (!TensuraTNOCompatConfig.isVanillaBackportDelayedSheepLayerEnabled()) {
            return;
        }

        if (pendingRegistrations.isEmpty()) {
            return;
        }

        for (int index = 0; index < pendingRegistrations.size(); ) {
            PendingLayerRegistration pending = pendingRegistrations.get(index);
            if (pending.tryApply()) {
                pendingRegistrations.remove(index);
                continue;
            }

            index++;
        }
    }

    public static boolean isConfigNotLoaded(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalStateException
                && current.getMessage() != null
                && current.getMessage().contains("Cannot get config value before config is loaded")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record PendingLayerRegistration(Supplier<?> supplier, Consumer<Object> consumer) {
        private boolean tryApply() {
            final Object layer;
            try {
                layer = supplier.get();
            } catch (RuntimeException exception) {
                if (isConfigNotLoaded(exception)) {
                    return false;
                }

                throw exception;
            }

            if (layer != null) {
                consumer.accept(layer);
            }

            return true;
        }
    }
}
