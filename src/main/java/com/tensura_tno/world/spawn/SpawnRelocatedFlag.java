package com.tensura_tno.world.spawn;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * World-level {@link SavedData} that records whether the overworld's initial
 * shared spawn position has already been considered for biome-blacklist
 * relocation in this save.
 *
 * <p>The flag is intentionally minimal: it carries a single {@code boolean}
 * persisted under {@link #DATA_NAME} in the dimension data storage. Once the
 * {@code SpawnRelocator} has finished (or skipped) its first attempt for a
 * given save, it calls {@link #markDone()} to ensure subsequent server
 * starts do not re-run the spiral search and do not overwrite the spawn
 * coordinate again. This implements the "at most once per save" guarantee
 * from Requirement 1.4.
 *
 * <p>Construction goes through the factory returned by
 * {@link #get(ServerLevel)} which delegates to
 * {@link DimensionDataStorage#computeIfAbsent} so the instance is
 * automatically created, loaded from disk, and persisted as part of the
 * save's normal lifecycle.
 *
 * @see net.minecraft.world.level.saveddata.SavedData
 */
public final class SpawnRelocatedFlag extends SavedData {

    /**
     * Name under which this {@link SavedData} is stored in
     * {@code <save>/data/tensura_tno_spawn_relocated.dat}. Must remain
     * stable across versions to avoid invalidating existing saves.
     */
    static final String DATA_NAME = "tensura_tno_spawn_relocated";

    /** NBT key for the persisted boolean field. */
    private static final String NBT_KEY_DONE = "done";

    private boolean done;

    private SpawnRelocatedFlag() {
        this.done = false;
    }

    /**
     * Factory used by the {@link SavedData.Factory} when the flag is
     * created for the first time on a fresh save.
     */
    private static SpawnRelocatedFlag create() {
        return new SpawnRelocatedFlag();
    }

    /**
     * Deserializer used by the {@link SavedData.Factory} when an existing
     * record is loaded from disk.
     *
     * @param tag      the persisted compound (never {@code null} when this
     *                 method is invoked by the data storage)
     * @param provider the registry holder lookup; unused here because the
     *                 payload is purely a primitive flag
     * @return a {@link SpawnRelocatedFlag} populated from {@code tag}
     */
    public static SpawnRelocatedFlag load(CompoundTag tag, HolderLookup.Provider provider) {
        SpawnRelocatedFlag flag = new SpawnRelocatedFlag();
        flag.done = tag.getBoolean(NBT_KEY_DONE);
        return flag;
    }

    /**
     * Returns the per-save singleton attached to {@code level}, creating
     * and registering it if it does not yet exist.
     *
     * @param level the {@link ServerLevel} whose data storage should host
     *              the flag (typically the overworld)
     * @return the live {@link SpawnRelocatedFlag} for {@code level}; never
     *         {@code null}
     */
    public static SpawnRelocatedFlag get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(SpawnRelocatedFlag::create, SpawnRelocatedFlag::load, null),
                DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean(NBT_KEY_DONE, this.done);
        return tag;
    }

    /**
     * Returns whether the relocation pass has already been performed (or
     * intentionally skipped) for this save.
     *
     * @return {@code true} once {@link #markDone()} has been called and
     *         the change has been persisted; {@code false} otherwise
     */
    public boolean isDone() {
        return this.done;
    }

    /**
     * Marks the relocation pass as completed for this save and signals the
     * data storage that the underlying record is dirty and must be written
     * to disk on the next save tick.
     */
    public void markDone() {
        this.done = true;
        setDirty();
    }
}
