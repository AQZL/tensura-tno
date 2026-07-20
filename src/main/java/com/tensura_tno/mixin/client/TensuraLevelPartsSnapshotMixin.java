package com.tensura_tno.mixin.client;

import io.github.manasmods.tensura.entity.template.TensuraPartEntity;
import io.github.manasmods.tensura.world.subclass.IMultipartLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Fixes a server hang caused by an infinite loop in fastutil's Int2ObjectOpenHashMap iterator.
 *
 * Root cause: tensura's MixinLevel.getEntityParts injects at TAIL of Level.getEntities and
 * iterates tensura$parts (an Int2ObjectOpenHashMap) via this.getParts().iterator(). When the
 * map is modified during iteration (e.g., EntityJoinLevelEvent / EntityLeaveLevelEvent fires),
 * fastutil's open-addressing iterator enters an infinite loop without throwing
 * ConcurrentModificationException, freezing the server tick watchdog.
 *
 * Fix: Override getParts() with priority 1100 (> tensura's default 1000) to return an
 * ArrayList snapshot of the values collection. This guarantees the iterator always operates
 * on a stable copy, preventing concurrent-modification infinite loops.
 */
@Mixin(value = Level.class, priority = 1100)
public abstract class TensuraLevelPartsSnapshotMixin implements IMultipartLevel {

    @Shadow(remap = false)
    public abstract Int2ObjectMap<TensuraPartEntity> tensura$getParts();

    @Override
    public Collection<TensuraPartEntity> getParts() {
        // Return a snapshot copy so iterators in tensura's getEntityParts Mixin injection
        // are never invalidated by concurrent put/remove operations on tensura$parts.
        return new ArrayList<>(this.tensura$getParts().values());
    }
}
