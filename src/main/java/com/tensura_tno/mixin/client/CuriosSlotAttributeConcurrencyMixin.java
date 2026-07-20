package com.tensura_tno.mixin.client;

import com.tensura_tno.TensuraTNOCompatConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.api.SlotAttribute", remap = false)
public class CuriosSlotAttributeConcurrencyMixin {

    @Unique
    private static final Map<String, Holder<? extends Attribute>> tensura_tno$fallbackSlotAttributes =
        new ConcurrentHashMap<>();

    @Unique
    private static volatile Map<String, Holder<? extends Attribute>> tensura_tno$slotAttributes;

    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tensura_tno$makeGetOrCreateThreadSafe(String id,
                                                              CallbackInfoReturnable<Holder<Attribute>> cir) {
        if (!TensuraTNOCompatConfig.isCuriosSlotAttributeConcurrencyEnabled()) {
            return;
        }

        Map<String, Holder<? extends Attribute>> slotAttributes = tensura_tno$getSlotAttributes();

        if (slotAttributes == null) {
            cir.setReturnValue(tensura_tno$computeFallback(id));
            return;
        }

        synchronized (slotAttributes) {
            @SuppressWarnings("unchecked")
            Holder<Attribute> holder = (Holder<Attribute>) slotAttributes.computeIfAbsent(id,
                tensura_tno$makeAttributeHolder());
            cir.setReturnValue(holder);
        }
    }

    @Unique
    private static Map<String, Holder<? extends Attribute>> tensura_tno$getSlotAttributes() {
        Map<String, Holder<? extends Attribute>> slotAttributes = tensura_tno$slotAttributes;

        if (slotAttributes != null) {
            return slotAttributes;
        }

        try {
            Class<?> slotAttributeClass = Class.forName("top.theillusivec4.curios.api.SlotAttribute");
            Field field = slotAttributeClass.getDeclaredField("SLOT_ATTRIBUTES");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Holder<? extends Attribute>> resolved =
                (Map<String, Holder<? extends Attribute>>) field.get(null);
            tensura_tno$slotAttributes = resolved;
            return resolved;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    @Unique
    private static java.util.function.Function<String, Holder<? extends Attribute>> tensura_tno$makeAttributeHolder() {
        return identifier -> {
            try {
                Class<?> slotAttributeClass = Class.forName("top.theillusivec4.curios.api.SlotAttribute");
                Constructor<?> constructor = slotAttributeClass.getDeclaredConstructor(String.class);
                constructor.setAccessible(true);
                return new Holder.Direct<>((Attribute) constructor.newInstance(identifier));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to create Curios slot attribute for " + identifier, exception);
            }
        };
    }

    @Unique
    private static Holder<Attribute> tensura_tno$computeFallback(String id) {
        @SuppressWarnings("unchecked")
        Holder<Attribute> holder = (Holder<Attribute>) tensura_tno$fallbackSlotAttributes.computeIfAbsent(id,
            tensura_tno$makeAttributeHolder());
        return holder;
    }
}