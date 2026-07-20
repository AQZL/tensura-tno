package com.tensura_tno.world.spawn;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SpawnRelocatedFlag} covering the SavedData
 * serialization round-trip and the {@code markDone} → {@code setDirty}
 * contract.
 *
 * <p>Validates Requirement 1.4 (the relocation flag must persist across
 * save / load cycles so the world-level relocation pass runs at most once
 * per save).
 *
 * <p>Notes on construction: {@link SpawnRelocatedFlag}'s constructor and
 * {@code create()} are intentionally private — they are wired into the
 * dimension data storage through a {@link net.minecraft.world.level.saveddata.SavedData.Factory}
 * obtained via {@link SpawnRelocatedFlag#get}. Outside of that factory the
 * cleanest test entry point is {@link SpawnRelocatedFlag#load(CompoundTag,
 * net.minecraft.core.HolderLookup.Provider)} with an empty tag, which
 * yields a fresh instance with {@code done == false}. This is the path the
 * tests below use.
 */
class SpawnRelocatedFlagTest {

    /**
     * Helper: build a fresh flag with {@code done == false} via the public
     * {@code load(...)} entry point with an empty NBT.
     */
    private static SpawnRelocatedFlag freshFlag() {
        return SpawnRelocatedFlag.load(new CompoundTag(), null);
    }

    @Test
    @DisplayName("roundTrip_doneTrue_persists: markDone() → save() → load() preserves done=true")
    void roundTrip_doneTrue_persists() {
        SpawnRelocatedFlag original = freshFlag();
        original.markDone();
        assertTrue(original.isDone(), "precondition: markDone() must flip done to true");

        CompoundTag persisted = original.save(new CompoundTag(), null);
        SpawnRelocatedFlag restored = SpawnRelocatedFlag.load(persisted, null);

        assertTrue(restored.isDone(),
                "done=true must survive a save/load round trip");
    }

    @Test
    @DisplayName("roundTrip_doneFalse_persists: an unmarked flag stays false after save() → load()")
    void roundTrip_doneFalse_persists() {
        SpawnRelocatedFlag original = freshFlag();
        assertFalse(original.isDone(), "precondition: a freshly loaded flag must start as done=false");

        CompoundTag persisted = original.save(new CompoundTag(), null);
        SpawnRelocatedFlag restored = SpawnRelocatedFlag.load(persisted, null);

        assertFalse(restored.isDone(),
                "done=false must survive a save/load round trip");
    }

    @Test
    @DisplayName("markDone_setsDirty: markDone() flips the inherited SavedData dirty flag to true")
    void markDone_setsDirty() {
        SpawnRelocatedFlag flag = freshFlag();
        assertFalse(flag.isDirty(),
                "precondition: a freshly loaded flag must not start dirty");

        flag.markDone();

        assertTrue(flag.isDirty(),
                "markDone() must call setDirty() so DimensionDataStorage will write the change");
    }
}
