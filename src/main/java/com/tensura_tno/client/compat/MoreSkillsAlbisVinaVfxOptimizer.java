package com.tensura_tno.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lightweight, fail-open LOD policy for Albis Vina's standalone renderer. */
public final class MoreSkillsAlbisVinaVfxOptimizer {
    private static final double NEAR_DISTANCE_SQUARED = 32.0D * 32.0D;
    private static final double MID_DISTANCE_SQUARED = 80.0D * 80.0D;

    private MoreSkillsAlbisVinaVfxOptimizer() {
    }

    public static boolean isImpactVisible(Vec3 origin, float radius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || origin == null) {
            return true;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double renderDistance = Math.max(160.0D, minecraft.options.renderDistance().get() * 16.0D + 48.0D);
        double extent = Math.max(3.0D, Math.abs(radius) + 3.0D);
        return distanceSquaredToAabb(camera, new AABB(origin, origin).inflate(extent, 5.0D, extent))
                <= renderDistance * renderDistance;
    }

    public static boolean isBeamVisible(Vec3 start, Vec3 end, float radius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || start == null || end == null) {
            return true;
        }
        double padding = Math.max(2.0D, Math.abs(radius) * 2.0D);
        AABB bounds = new AABB(
                Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)
        ).inflate(padding);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double renderDistance = Math.max(160.0D, minecraft.options.renderDistance().get() * 16.0D + 48.0D);
        return distanceSquaredToAabb(camera, bounds) <= renderDistance * renderDistance;
    }

    public static int count(int original, Vec3 origin) {
        return Math.min(original, Math.max(1, (int) Math.ceil(original * factor(origin))));
    }

    public static int rings(int original, Vec3 origin) {
        return Math.max(5, count(original, origin));
    }

    public static int segments(int original, Vec3 origin) {
        return Math.max(48, count(original, origin));
    }

    private static double factor(Vec3 origin) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || origin == null) {
            return 1.0D;
        }
        double distanceSquared = minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(origin);
        int tier = distanceSquared <= NEAR_DISTANCE_SQUARED ? 0
                : distanceSquared <= MID_DISTANCE_SQUARED ? 1 : 2;
        ParticleStatus particles = minecraft.options.particles().get();
        if (particles == ParticleStatus.DECREASED) {
            tier++;
        } else if (particles == ParticleStatus.MINIMAL) {
            tier += 2;
        }
        return switch (Math.min(2, tier)) {
            case 0 -> 0.50D;
            case 1 -> 0.25D;
            default -> 0.10D;
        };
    }

    private static double distanceSquaredToAabb(Vec3 point, AABB bounds) {
        double dx = Math.max(Math.max(bounds.minX - point.x, 0.0D), point.x - bounds.maxX);
        double dy = Math.max(Math.max(bounds.minY - point.y, 0.0D), point.y - bounds.maxY);
        double dz = Math.max(Math.max(bounds.minZ - point.z, 0.0D), point.z - bounds.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }
}
