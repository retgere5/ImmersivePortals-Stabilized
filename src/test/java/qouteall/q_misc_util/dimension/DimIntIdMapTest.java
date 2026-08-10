package qouteall.q_misc_util.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the world-bricking crash (#54 class):
 * {@code DimIntIdMap.toIntegerId} used to throw a RuntimeException for any
 * dimension that was not present in the map at build time (e.g. AE2's
 * ae2:spatial_storage, or any datapack dimension created after the map was
 * built). Thrown from the server tick loop (EntitySync -> PacketRedirection
 * -> PortalAPI.serverDimKeyToInt -> DimIntIdMap.toIntegerId), that exception
 * permanently bricks the world.
 * <p>
 * Contract under test: toIntegerId must never throw for a dimension key that
 * is otherwise a normal, valid ResourceKey<Level> -- unknown keys must be
 * lazily (and stably) assigned an id instead.
 */
public class DimIntIdMapTest {

    private static ResourceKey<Level> dim(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static DimIntIdMap mapWithVanillaDims() {
        DimIntIdMap map = new DimIntIdMap();
        map.add(Level.OVERWORLD, 0);
        map.add(Level.NETHER, -1);
        map.add(Level.END, 1);
        return map;
    }

    @Test
    public void unknownDimensionDoesNotThrow() {
        DimIntIdMap map = mapWithVanillaDims();
        ResourceKey<Level> unknown = dim("ae2", "spatial_storage");

        assertDoesNotThrow(() -> map.toIntegerId(unknown));
    }

    @Test
    public void unknownDimensionGetsStableIdAcrossCalls() {
        DimIntIdMap map = mapWithVanillaDims();
        ResourceKey<Level> unknown = dim("ae2", "spatial_storage");

        int first = map.toIntegerId(unknown);
        int second = map.toIntegerId(unknown);

        assertEquals(first, second, "repeated lookups of the same unknown dimension must yield the same id");
    }

    @Test
    public void existingIdsUnchangedAfterLazyAssignment() {
        DimIntIdMap map = mapWithVanillaDims();

        int overworldBefore = map.toIntegerId(Level.OVERWORLD);
        int netherBefore = map.toIntegerId(Level.NETHER);
        int endBefore = map.toIntegerId(Level.END);

        map.toIntegerId(dim("ae2", "spatial_storage"));
        map.toIntegerId(dim("mypack", "custom_dim"));

        assertEquals(overworldBefore, map.toIntegerId(Level.OVERWORLD));
        assertEquals(netherBefore, map.toIntegerId(Level.NETHER));
        assertEquals(endBefore, map.toIntegerId(Level.END));
    }

    @Test
    public void lazilyAssignedIdRoundTripsThroughFromIntegerId() {
        DimIntIdMap map = mapWithVanillaDims();
        ResourceKey<Level> unknown = dim("ae2", "spatial_storage");

        int assigned = map.toIntegerId(unknown);

        assertEquals(unknown, map.fromIntegerId(assigned));
        assertTrue(map.containsDimId(unknown));
    }

    @Test
    public void distinctUnknownDimensionsGetDistinctIds() {
        DimIntIdMap map = mapWithVanillaDims();

        int a = map.toIntegerId(dim("ae2", "spatial_storage"));
        int b = map.toIntegerId(dim("mypack", "custom_dim"));

        assertTrue(a != b, "distinct unknown dimensions must not collide on the same integer id");
    }

    // --- Sync-flag coverage: DimIntIdMap#isDirty/#consumeDirty are the hook that lets
    // the server-side caller (DimensionIntId#serverDimKeyToInt) know a lazy assignment
    // happened, so it can re-sync the map to clients through the existing
    // DimIdSyncPacket mechanism instead of leaving clients with a stale mapping. ---

    @Test
    public void lazyAssignmentMarksMapDirtyForClientSync() {
        DimIntIdMap map = mapWithVanillaDims();
        assertFalse(map.isDirty(), "freshly built map must not be dirty");

        map.toIntegerId(dim("ae2", "spatial_storage"));

        assertTrue(map.isDirty(), "lazy assignment must flag the map dirty so callers know to re-sync clients");
        assertTrue(map.consumeDirty());
        assertFalse(map.isDirty(), "consumeDirty must clear the flag");
    }

    @Test
    public void lookingUpKnownDimensionDoesNotMarkDirty() {
        DimIntIdMap map = mapWithVanillaDims();

        map.toIntegerId(Level.OVERWORLD);

        assertFalse(map.isDirty());
    }
}
