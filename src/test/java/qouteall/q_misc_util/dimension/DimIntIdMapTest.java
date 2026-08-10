package qouteall.q_misc_util.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    // --- Thread-safety coverage ---
    //
    // toIntegerId stopped being a pure read once it could lazily add() a new
    // entry, and it is reachable off the server thread: packet redirection
    // (PacketRedirection.createRedirectedMessage / withForceRedirectAndGet)
    // only logs a warning on a thread mismatch, it does not prevent one (seen
    // in practice as a real ForkJoinWorkerThread stack reaching
    // withForceRedirectAndGet off the server thread). Before the
    // fix, two threads racing a lazy assignment could both observe MISSING_ID
    // and then collide in add() ("Dimension Id Record already contains ..."),
    // or corrupt the backing fastutil maps during a concurrent resize. This
    // test hammers toIntegerId from many threads, racing both the SAME
    // unknown key (must converge on one id) and DIFFERENT unknown keys (must
    // not collide), repeated many times to make a real race likely to surface.

    @Test
    public void concurrentLazyAssignmentIsThreadSafe() throws InterruptedException {
        DimIntIdMap map = mapWithVanillaDims();

        final int threadCount = 8;
        final int iterations = 200;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int iter = 0; iter < iterations; iter++) {
                ResourceKey<Level> sharedUnknown = dim("racepack", "shared_" + iter);
                List<ResourceKey<Level>> perThreadUnknown = new ArrayList<>();
                for (int t = 0; t < threadCount; t++) {
                    perThreadUnknown.add(dim("racepack", "distinct_" + iter + "_" + t));
                }

                CountDownLatch startGate = new CountDownLatch(1);
                CountDownLatch doneLatch = new CountDownLatch(threadCount * 2);
                int[] sharedResults = new int[threadCount];
                int[] distinctResults = new int[threadCount];

                for (int t = 0; t < threadCount; t++) {
                    final int threadIndex = t;

                    // half the work: every thread races on the exact same unknown key
                    executor.submit(() -> {
                        try {
                            startGate.await();
                            sharedResults[threadIndex] = map.toIntegerId(sharedUnknown);
                        }
                        catch (Throwable thrown) {
                            errors.add(thrown);
                        }
                        finally {
                            doneLatch.countDown();
                        }
                    });

                    // the other half: each thread races on its own distinct unknown key
                    executor.submit(() -> {
                        try {
                            startGate.await();
                            distinctResults[threadIndex] = map.toIntegerId(perThreadUnknown.get(threadIndex));
                        }
                        catch (Throwable thrown) {
                            errors.add(thrown);
                        }
                        finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startGate.countDown();
                assertTrue(
                    doneLatch.await(30, TimeUnit.SECONDS),
                    "threads did not finish in time on iteration " + iter
                );

                assertTrue(errors.isEmpty(), "concurrent toIntegerId calls must not throw: " + errors);

                // all racers on the same key must have converged on one id
                int expectedSharedId = sharedResults[0];
                for (int id : sharedResults) {
                    assertEquals(expectedSharedId, id, "all threads racing the same unknown key must get the same id (iter " + iter + ")");
                }
                assertEquals(sharedUnknown, map.fromIntegerId(expectedSharedId));

                // distinct unknown keys must not collide with each other or with the shared key
                Set<Integer> seenIds = new HashSet<>();
                seenIds.add(expectedSharedId);
                for (int t = 0; t < threadCount; t++) {
                    int id = distinctResults[t];
                    assertTrue(
                        seenIds.add(id),
                        "distinct unknown dimensions must not collide on id " + id + " (iter " + iter + ")"
                    );
                    assertEquals(perThreadUnknown.get(t), map.fromIntegerId(id));
                }
            }
        }
        finally {
            executor.shutdownNow();
        }

        assertTrue(errors.isEmpty(), "concurrent toIntegerId calls must not throw: " + errors);
    }
}
