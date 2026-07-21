package com.tensura_tno.mixin.client;

import com.mojang.blaze3d.shaders.Uniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Caches stable post-chain reflection without changing any shader input or pass. */
@Pseudo
@Mixin(targets = "com.github.wal_bos.moreskills.client.fx.AsmodayBlackHoleClient", remap = false)
public abstract class MoreSkillsAsmodayBlackHoleReflectionCacheMixin {
    @Unique
    private static final Object tensuraTno$noUniform = new Object();
    @Unique
    private static final Map<Class<?>, Field[]> tensuraTno$passFields = new IdentityHashMap<>();
    @Unique
    private static final Map<Class<?>, AccessibleObject> tensuraTno$shaderAccessors = new IdentityHashMap<>();
    @Unique
    private static final Map<Class<?>, Method> tensuraTno$uniformAccessors = new IdentityHashMap<>();
    @Unique
    private static final Map<Object, Map<String, Object>> tensuraTno$uniforms = new IdentityHashMap<>();
    @Unique
    private static Object tensuraTno$currentEffect;

    @Inject(
            method = "getPasses(Ljava/lang/Object;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$getCachedPasses(Object effect, CallbackInfoReturnable<List<?>> cir) {
        if (effect == null) {
            return;
        }

        tensuraTno$observeEffect(effect);
        Field[] fields = tensuraTno$passFields.get(effect.getClass());
        if (fields == null) {
            try {
                List<Field> candidates = new ArrayList<>();
                for (Field field : effect.getClass().getDeclaredFields()) {
                    if (List.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        candidates.add(field);
                    }
                }
                fields = candidates.toArray(Field[]::new);
                tensuraTno$passFields.put(effect.getClass(), fields);
            } catch (RuntimeException exception) {
                return;
            }
        }

        try {
            for (Field field : fields) {
                Object value = field.get(effect);
                if (value instanceof List<?> list && !list.isEmpty()) {
                    cir.setReturnValue(list);
                    return;
                }
            }
            cir.setReturnValue(List.of());
        } catch (IllegalAccessException | RuntimeException exception) {
            tensuraTno$passFields.remove(effect.getClass());
        }
    }

    @Inject(
            method = "getShader(Ljava/lang/Object;)Ljava/lang/Object;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$getCachedShader(Object pass, CallbackInfoReturnable<Object> cir) {
        if (pass == null) {
            return;
        }

        Class<?> passClass = pass.getClass();
        AccessibleObject accessor = tensuraTno$shaderAccessors.get(passClass);
        if (accessor == null) {
            try {
                for (Method method : passClass.getDeclaredMethods()) {
                    if (method.getParameterCount() == 0
                            && method.getReturnType().getSimpleName().equals("EffectInstance")) {
                        method.setAccessible(true);
                        accessor = method;
                        break;
                    }
                }
                if (accessor == null) {
                    for (Field field : passClass.getDeclaredFields()) {
                        if (field.getType().getSimpleName().equals("EffectInstance")) {
                            field.setAccessible(true);
                            accessor = field;
                            break;
                        }
                    }
                }
                if (accessor == null) {
                    return;
                }
                tensuraTno$shaderAccessors.put(passClass, accessor);
            } catch (RuntimeException exception) {
                return;
            }
        }

        try {
            Object shader = accessor instanceof Method method ? method.invoke(pass) : ((Field) accessor).get(pass);
            cir.setReturnValue(shader);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            tensuraTno$shaderAccessors.remove(passClass);
        }
    }

    @Inject(
            method = "setUniform1(Ljava/lang/Object;Ljava/lang/String;F)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$setCachedUniform1(
            Object shader, String name, float value, CallbackInfo ci
    ) {
        Object uniform = tensuraTno$getUniform(shader, name);
        if (uniform == tensuraTno$noUniform) {
            ci.cancel();
        } else if (uniform instanceof Uniform resolved) {
            resolved.set(value);
            ci.cancel();
        }
    }

    @Inject(
            method = "setUniform2(Ljava/lang/Object;Ljava/lang/String;FF)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void tensuraTno$setCachedUniform2(
            Object shader, String name, float x, float y, CallbackInfo ci
    ) {
        Object uniform = tensuraTno$getUniform(shader, name);
        if (uniform == tensuraTno$noUniform) {
            ci.cancel();
        } else if (uniform instanceof Uniform resolved) {
            resolved.set(x, y);
            ci.cancel();
        }
    }

    @Inject(method = "disablePostShader()V", at = @At("TAIL"), require = 0, remap = false)
    private static void tensuraTno$clearPostObjects(CallbackInfo ci) {
        tensuraTno$currentEffect = null;
        tensuraTno$uniforms.clear();
    }

    @Unique
    private static void tensuraTno$observeEffect(Object effect) {
        if (tensuraTno$currentEffect != effect) {
            tensuraTno$currentEffect = effect;
            tensuraTno$uniforms.clear();
        }
    }

    @Unique
    private static Object tensuraTno$getUniform(Object shader, String name) {
        if (shader == null) {
            return tensuraTno$noUniform;
        }

        Map<String, Object> cached = tensuraTno$uniforms.get(shader);
        if (cached != null && cached.containsKey(name)) {
            return cached.get(name);
        }

        Class<?> shaderClass = shader.getClass();
        Method accessor = tensuraTno$uniformAccessors.get(shaderClass);
        try {
            if (accessor == null) {
                accessor = shaderClass.getMethod("safeGetUniform", String.class);
                tensuraTno$uniformAccessors.put(shaderClass, accessor);
            }
            Object result = accessor.invoke(shader, name);
            Object resolved = result instanceof Uniform ? result : tensuraTno$noUniform;
            tensuraTno$uniforms.computeIfAbsent(shader, ignored -> new java.util.HashMap<>()).put(name, resolved);
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            tensuraTno$uniformAccessors.remove(shaderClass);
            return null;
        }
    }
}
