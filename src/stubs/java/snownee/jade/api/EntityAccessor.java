package snownee.jade.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public interface EntityAccessor extends Accessor<EntityHitResult> {
    Entity getEntity();

    Entity getRawEntity();
}
