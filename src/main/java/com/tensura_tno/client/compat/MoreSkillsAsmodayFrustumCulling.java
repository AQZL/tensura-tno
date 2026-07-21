package com.tensura_tno.client.compat;

import java.lang.reflect.Field;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Owns Asmoday culling state outside the package reserved for Mixin classes. */
public final class MoreSkillsAsmodayFrustumCulling {
    /* Distant warped dust reaches 10.892 radii horizontally; polar jets reach 5.85 vertically. */
    private static final double HORIZONTAL_EXTENT_SCALE = 11.0D;
    private static final double VERTICAL_EXTENT_SCALE = 6.0D;
    private static final double EXTENT_PADDING = 1.0D;

    private static final ThreadLocal<Frustum> FRAME_FRUSTUM = new ThreadLocal<>();
    private static final ClassValue<HoleFields> HOLE_FIELDS = new ClassValue<>() {
        @Override
        protected HoleFields computeValue(Class<?> type) {
            return HoleFields.inspect(type);
        }
    };

    private MoreSkillsAsmodayFrustumCulling() {
    }

    public static void beginFrame(RenderLevelStageEvent event) {
        FRAME_FRUSTUM.remove();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES && event.getFrustum() != null) {
            FRAME_FRUSTUM.set(event.getFrustum());
        }
    }

    public static void endFrame() {
        FRAME_FRUSTUM.remove();
    }

    public static boolean shouldCull(Object hole) {
        Frustum frustum = FRAME_FRUSTUM.get();
        if (frustum == null || hole == null) {
            return false;
        }
        return HOLE_FIELDS.get(hole.getClass()).isFullyOutside(hole, frustum);
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static final class HoleFields {
        private static final HoleFields INVALID = new HoleFields(null, null);

        private final Field center;
        private final Field radius;

        private HoleFields(Field center, Field radius) {
            this.center = center;
            this.radius = radius;
        }

        private static HoleFields inspect(Class<?> type) {
            try {
                Field center = type.getDeclaredField("center");
                Field radius = type.getDeclaredField("radius");
                if (!center.trySetAccessible() || !radius.trySetAccessible()) {
                    return INVALID;
                }
                return new HoleFields(center, radius);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return INVALID;
            }
        }

        private boolean isFullyOutside(Object hole, Frustum frustum) {
            if (this == INVALID) {
                return false;
            }
            try {
                Object centerValue = center.get(hole);
                Object radiusValue = radius.get(hole);
                if (!(centerValue instanceof Vec3 center) || !(radiusValue instanceof Number number)) {
                    return false;
                }
                float radiusValueFloat = number.floatValue();
                if (!isFinite(center) || !Float.isFinite(radiusValueFloat)) {
                    return false;
                }

                double radius = Math.abs((double) radiusValueFloat);
                double horizontal = radius * HORIZONTAL_EXTENT_SCALE + EXTENT_PADDING;
                double vertical = radius * VERTICAL_EXTENT_SCALE + EXTENT_PADDING;
                if (!Double.isFinite(horizontal) || !Double.isFinite(vertical)) {
                    return false;
                }

                AABB bounds = new AABB(
                        center.x - horizontal,
                        center.y - vertical,
                        center.z - horizontal,
                        center.x + horizontal,
                        center.y + vertical,
                        center.z + horizontal
                );
                return !frustum.isVisible(bounds);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
    }
}
