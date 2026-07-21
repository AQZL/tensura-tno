package com.tensura_tno.client.compat;

import com.tensura_tno.TensuraTNOMod;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side visibility and detail policy for MoreSkills' Elfaria effects.
 * All reflective paths fail open so a changed MoreSkills layout keeps its
 * original rendering instead of losing effects.
 */
public final class MoreSkillsElfariaVfxOptimizer {
    private static final double NEAR_DISTANCE = 64.0D;
    private static final double MID_DISTANCE = 160.0D;
    private static final double MINIMUM_RENDER_DISTANCE = 256.0D;
    private static final double RENDER_DISTANCE_PADDING = 96.0D;
    private static final MeshBudget ORIGINAL_MESH_BUDGET = new MeshBudget(18, 144, 18, 26, 280, 96);
    private static final MeshBudget NEAR_MESH_BUDGET = new MeshBudget(12, 96, 12, 18, 180, 64);
    private static final MeshBudget MID_MESH_BUDGET = new MeshBudget(8, 64, 8, 12, 96, 40);
    private static final MeshBudget FAR_MESH_BUDGET = new MeshBudget(4, 36, 4, 8, 48, 20);

    private static final String[] EFFECT_RENDERERS = {
            "com.github.wal_bos.moreskills.client.fx.ElfariaMyrdasFridolieteVfxClient",
            "com.github.wal_bos.moreskills.client.fx.ElfariaFruzelCardeneiaVfxClient",
            "com.github.wal_bos.moreskills.client.fx.ElfariaGlaciaLastAlbisVfxClient",
            "com.github.wal_bos.moreskills.client.fx.ElfariaStellasNateaVfxClient",
            "com.github.wal_bos.moreskills.client.fx.ElfariaPassiveIceStepsVfxClient",
            "com.github.wal_bos.moreskills.client.fx.ElfariaArsWeissBloomVfxClient",
            "com.github.wal_bos.moreskills.client.fx.ElfariaOpIceShineVfxClient"
    };

    private static final IdentityHashMap<List<?>, Selection> FRAME_SELECTIONS = new IdentityHashMap<>();
    private static final ClassValue<EffectLayout> EFFECT_LAYOUTS = new ClassValue<>() {
        @Override
        protected EffectLayout computeValue(Class<?> type) {
            return EffectLayout.inspect(type);
        }
    };

    private static FrameContext frameContext;
    private static Detail currentDetail = Detail.ORIGINAL;
    private static ClientLevel observedLevel;
    private static boolean reflectionWarningLogged;

    private MoreSkillsElfariaVfxOptimizer() {
    }

    public static void beginFrame(RenderLevelStageEvent event) {
        endFrame();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || event.getCamera() == null) {
            return;
        }

        int renderDistanceChunks = minecraft.options.renderDistance().get();
        double renderDistance = Math.max(
                MINIMUM_RENDER_DISTANCE,
                renderDistanceChunks * 16.0D + RENDER_DISTANCE_PADDING
        );
        frameContext = new FrameContext(
                event.getCamera().getPosition(),
                event.getFrustum(),
                renderDistance * renderDistance,
                particlePenalty(minecraft.options.particles().get())
        );
    }

    public static void endFrame() {
        frameContext = null;
        currentDetail = Detail.ORIGINAL;
        FRAME_SELECTIONS.clear();
    }

    public static void observeClientLevel() {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (observedLevel == null) {
            observedLevel = currentLevel;
            return;
        }
        if (currentLevel != observedLevel) {
            clearEffectCollections();
            observedLevel = currentLevel;
        }
    }

    public static boolean isEffectivelyEmpty(List<?> effects) {
        if (frameContext == null || effects.isEmpty()) {
            return effects.isEmpty();
        }
        return selectionFor(effects).effects().isEmpty();
    }

    public static Iterator<?> visibleIterator(List<?> effects) {
        if (frameContext == null || effects.isEmpty()) {
            currentDetail = Detail.ORIGINAL;
            return effects.iterator();
        }
        return new DetailIterator(selectionFor(effects).effects().iterator());
    }

    public static boolean shouldOptimizeIceMesh() {
        return frameContext != null && currentDetail != Detail.ORIGINAL;
    }

    public static boolean isGeometryVisible(Vec3 cameraRelativeCenter, double extent) {
        FrameContext context = frameContext;
        if (context == null || currentDetail == Detail.ORIGINAL) {
            return true;
        }
        if (!isFinite(cameraRelativeCenter) || !Double.isFinite(extent)) {
            return true;
        }

        double safeExtent = Math.max(0.25D, Math.abs(extent));
        Vec3 worldCenter = cameraRelativeCenter.add(context.cameraPosition());
        AABB bounds = new AABB(worldCenter, worldCenter).inflate(safeExtent);
        return isVisible(context, bounds);
    }

    public static int discRings() {
        return meshBudget(currentDetail).discRings();
    }

    public static int discSegments() {
        return meshBudget(currentDetail).discSegments();
    }

    public static int rectUSteps() {
        return meshBudget(currentDetail).rectUSteps();
    }

    public static int rectVSteps() {
        return meshBudget(currentDetail).rectVSteps();
    }

    public static int discLineCount(int original) {
        return Math.min(original, meshBudget(currentDetail).discLines());
    }

    public static int sheenCount(int original) {
        return Math.min(original, meshBudget(currentDetail).sheenLines());
    }

    static Detail detailForDistanceSquared(double distanceSquared, int particlePenalty) {
        Detail detail;
        if (distanceSquared <= NEAR_DISTANCE * NEAR_DISTANCE) {
            detail = Detail.NEAR;
        } else if (distanceSquared <= MID_DISTANCE * MID_DISTANCE) {
            detail = Detail.MID;
        } else {
            detail = Detail.FAR;
        }

        int shifted = Math.min(Detail.FAR.ordinal(), detail.ordinal() + Math.max(0, particlePenalty));
        return Detail.values()[shifted];
    }

    static int renderBudget(String simpleName, int particlePenalty) {
        EffectRule rule = EffectRule.forName(simpleName);
        if (rule == null) {
            return Integer.MAX_VALUE;
        }
        double multiplier = switch (Math.max(0, particlePenalty)) {
            case 0 -> 1.0D;
            case 1 -> 0.75D;
            default -> 0.5D;
        };
        return Math.max(1, (int) Math.ceil(rule.renderBudget() * multiplier));
    }

    static double effectExtent(String simpleName, double radius) {
        EffectRule rule = EffectRule.forName(simpleName);
        return rule == null
                ? Double.POSITIVE_INFINITY
                : rule.padding() + Math.abs(radius) * rule.radiusScale();
    }

    static MeshBudget meshBudget(Detail detail) {
        return switch (detail) {
            case ORIGINAL -> ORIGINAL_MESH_BUDGET;
            case NEAR -> NEAR_MESH_BUDGET;
            case MID -> MID_MESH_BUDGET;
            case FAR -> FAR_MESH_BUDGET;
        };
    }

    private static Selection selectionFor(List<?> effects) {
        Selection cached = FRAME_SELECTIONS.get(effects);
        if (cached != null) {
            return cached;
        }

        Selection computed = buildSelection(effects);
        FRAME_SELECTIONS.put(effects, computed);
        return computed;
    }

    private static Selection buildSelection(List<?> effects) {
        if (effects.isEmpty()) {
            return Selection.EMPTY;
        }

        Object first = effects.getFirst();
        if (first == null) {
            return originalSelection(effects);
        }

        EffectRule rule = EffectRule.forName(first.getClass().getSimpleName());
        EffectLayout layout = EFFECT_LAYOUTS.get(first.getClass());
        if (rule == null || !layout.valid()) {
            return originalSelection(effects);
        }

        FrameContext context = frameContext;
        if (context == null) {
            return originalSelection(effects);
        }

        List<SelectedEffect> visible = new ArrayList<>(effects.size());
        try {
            for (int index = 0; index < effects.size(); index++) {
                Object effect = effects.get(index);
                if (effect == null || effect.getClass() != first.getClass()) {
                    return originalSelection(effects);
                }

                AABB bounds = layout.bounds(effect, rule);
                if (bounds == null) {
                    return originalSelection(effects);
                }
                if (!isVisible(context, bounds)) {
                    continue;
                }

                double distanceSquared = distanceSquaredToAabb(context.cameraPosition(), bounds);
                int priority = layout.priority(effect);
                Detail detail = detailForDistanceSquared(distanceSquared, context.particlePenalty());
                visible.add(new SelectedEffect(effect, detail, index, priority, distanceSquared));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnReflectionFailure(first.getClass(), exception);
            return originalSelection(effects);
        }

        int budget = renderBudget(first.getClass().getSimpleName(), context.particlePenalty());
        if (visible.size() > budget) {
            visible.sort(Comparator
                    .comparingInt(SelectedEffect::priority).reversed()
                    .thenComparingDouble(SelectedEffect::distanceSquared)
                    .thenComparingInt(SelectedEffect::sourceIndex));
            visible = new ArrayList<>(visible.subList(0, budget));
            visible.sort(Comparator.comparingInt(SelectedEffect::sourceIndex));
        }
        return new Selection(List.copyOf(visible));
    }

    private static Selection originalSelection(List<?> effects) {
        List<SelectedEffect> original = new ArrayList<>(effects.size());
        for (int index = 0; index < effects.size(); index++) {
            original.add(new SelectedEffect(effects.get(index), Detail.ORIGINAL, index, 0, 0.0D));
        }
        return new Selection(List.copyOf(original));
    }

    private static boolean isVisible(FrameContext context, AABB bounds) {
        Frustum frustum = context.frustum();
        if (frustum != null && !frustum.isVisible(bounds)) {
            return false;
        }
        return distanceSquaredToAabb(context.cameraPosition(), bounds) <= context.maximumDistanceSquared();
    }

    private static double distanceSquaredToAabb(Vec3 point, AABB bounds) {
        double dx = axisDistance(point.x, bounds.minX, bounds.maxX);
        double dy = axisDistance(point.y, bounds.minY, bounds.maxY);
        double dz = axisDistance(point.z, bounds.minZ, bounds.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0.0D;
    }

    private static int particlePenalty(ParticleStatus status) {
        return switch (status) {
            case ALL -> 0;
            case DECREASED -> 1;
            case MINIMAL -> 2;
        };
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static void clearEffectCollections() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String className : EFFECT_RENDERERS) {
            try {
                Class<?> renderer = Class.forName(className, false, loader);
                for (Field field : renderer.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())
                            || !Collection.class.isAssignableFrom(field.getType())
                            || !field.trySetAccessible()) {
                        continue;
                    }
                    Object value = field.get(null);
                    if (value instanceof Collection<?> collection) {
                        collection.clear();
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                warnReflectionFailure(null, exception);
                return;
            }
        }
    }

    private static void warnReflectionFailure(Class<?> effectType, Throwable throwable) {
        if (reflectionWarningLogged) {
            return;
        }
        reflectionWarningLogged = true;
        String target = effectType == null ? "renderer lists" : effectType.getName();
        TensuraTNOMod.LOGGER.warn(
                "[TensuraTNO] MoreSkills Elfaria optimization could not inspect {}; using original rendering",
                target,
                throwable
        );
    }

    enum Detail {
        ORIGINAL,
        NEAR,
        MID,
        FAR
    }

    record MeshBudget(
            int discRings,
            int discSegments,
            int rectUSteps,
            int rectVSteps,
            int discLines,
            int sheenLines
    ) {
    }

    private record FrameContext(
            Vec3 cameraPosition,
            Frustum frustum,
            double maximumDistanceSquared,
            int particlePenalty
    ) {
    }

    private record Selection(List<SelectedEffect> effects) {
        private static final Selection EMPTY = new Selection(List.of());
    }

    private record SelectedEffect(
            Object effect,
            Detail detail,
            int sourceIndex,
            int priority,
            double distanceSquared
    ) {
    }

    private record EffectRule(int renderBudget, double radiusScale, double padding) {
        private static final Map<String, EffectRule> RULES = Map.ofEntries(
                Map.entry("Barrier", new EffectRule(8, 2.5D, 8.0D)),
                Map.entry("FreezeField", new EffectRule(10, 4.0D, 8.0D)),
                Map.entry("Cathedral", new EffectRule(1, 5.0D, 24.0D)),
                Map.entry("Spike", new EffectRule(12, 4.0D, 8.0D)),
                Map.entry("Charge", new EffectRule(3, 22.0D, 32.0D)),
                Map.entry("Strike", new EffectRule(40, 16.0D, 8.0D)),
                Map.entry("Phoenix", new EffectRule(4, 42.0D, 64.0D)),
                Map.entry("Burst", new EffectRule(3, 9.0D, 24.0D)),
                Map.entry("Step", new EffectRule(48, 2.5D, 4.0D)),
                Map.entry("Bloom", new EffectRule(24, 2.0D, 6.0D)),
                Map.entry("Storm", new EffectRule(18, 4.0D, 20.0D)),
                Map.entry("Flight", new EffectRule(8, 32.0D, 48.0D))
        );

        private static EffectRule forName(String simpleName) {
            return RULES.get(simpleName);
        }
    }

    private record EffectLayout(
            List<Field> positionFields,
            List<Field> radiusFields,
            List<Field> priorityFields,
            boolean valid
    ) {
        private static EffectLayout inspect(Class<?> type) {
            List<Field> positions = new ArrayList<>();
            List<Field> radii = new ArrayList<>();
            List<Field> priorities = new ArrayList<>();

            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                boolean relevant = field.getType() == Vec3.class && isPositionField(name)
                        || isRadiusField(field, name)
                        || isPriorityField(field, name);
                if (!relevant || !field.trySetAccessible()) {
                    continue;
                }
                if (field.getType() == Vec3.class) {
                    positions.add(field);
                } else if (isRadiusField(field, name)) {
                    radii.add(field);
                } else {
                    priorities.add(field);
                }
            }
            return new EffectLayout(
                    List.copyOf(positions),
                    List.copyOf(radii),
                    List.copyOf(priorities),
                    !positions.isEmpty()
            );
        }

        private AABB bounds(Object effect, EffectRule rule) throws IllegalAccessException {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;

            for (Field field : positionFields) {
                Object raw = field.get(effect);
                if (!(raw instanceof Vec3 position) || !isFinite(position)) {
                    return null;
                }
                minX = Math.min(minX, position.x);
                minY = Math.min(minY, position.y);
                minZ = Math.min(minZ, position.z);
                maxX = Math.max(maxX, position.x);
                maxY = Math.max(maxY, position.y);
                maxZ = Math.max(maxZ, position.z);
            }

            double radius = 0.0D;
            for (Field field : radiusFields) {
                Object raw = field.get(effect);
                if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    return null;
                }
                radius = Math.max(radius, Math.abs(number.doubleValue()));
            }

            double extent = rule.padding() + radius * rule.radiusScale();
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(extent);
        }

        private int priority(Object effect) throws IllegalAccessException {
            int priority = 0;
            for (Field field : priorityFields) {
                if (field.getBoolean(effect)) {
                    String name = field.getName().toLowerCase(Locale.ROOT);
                    priority += name.equals("huge") ? 4 : name.equals("main") ? 3 : 1;
                }
            }
            return priority;
        }

        private static boolean isPositionField(String name) {
            return switch (name) {
                case "center", "caster", "start", "target", "visualend", "top", "impact", "pos",
                        "origin", "lastplayerpos" -> true;
                default -> false;
            };
        }

        private static boolean isRadiusField(Field field, String name) {
            Class<?> type = field.getType();
            return (type == float.class || type == double.class) && name.contains("radius");
        }

        private static boolean isPriorityField(Field field, String name) {
            return field.getType() == boolean.class
                    && (name.equals("main") || name.equals("huge") || name.equals("revive"));
        }
    }

    private static final class DetailIterator implements Iterator<Object> {
        private final Iterator<SelectedEffect> delegate;

        private DetailIterator(Iterator<SelectedEffect> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                currentDetail = Detail.ORIGINAL;
            }
            return hasNext;
        }

        @Override
        public Object next() {
            if (!delegate.hasNext()) {
                currentDetail = Detail.ORIGINAL;
                throw new NoSuchElementException();
            }
            SelectedEffect selected = delegate.next();
            currentDetail = selected.detail();
            return selected.effect();
        }
    }
}
