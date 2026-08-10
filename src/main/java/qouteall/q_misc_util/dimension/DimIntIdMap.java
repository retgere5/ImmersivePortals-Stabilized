package qouteall.q_misc_util.dimension;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.q_misc_util.Helper;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DimIntIdMap {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MISSING_ID = Integer.MIN_VALUE;

    final Object2IntOpenHashMap<ResourceKey<Level>> toIntegerId;
    final Int2ObjectOpenHashMap<ResourceKey<Level>> fromIntegerId;
    private int maxId;

    /**
     * Set by {@link #toIntegerId(ResourceKey)} whenever it has to lazily
     * assign an id to a dimension that was missing from the map (see #54:
     * dimensions created after this map was built -- e.g. AE2's spatial
     * storage level or a datapack dimension loaded at runtime -- used to
     * make {@code toIntegerId} throw a RuntimeException from the server
     * tick loop, permanently bricking the world). Server-side callers that
     * have access to the owning {@code MinecraftServer} (see
     * {@code DimensionIntId#serverDimKeyToInt}) should check this flag
     * after every {@code toIntegerId} call and, if set, re-sync the
     * updated map to clients via the existing {@code DimIdSyncPacket}
     * mechanism, then clear it with {@link #consumeDirty()}.
     */
    private boolean dirty = false;
    
    public DimIntIdMap(
        Object2IntOpenHashMap<ResourceKey<Level>> toIntegerId,
        Int2ObjectOpenHashMap<ResourceKey<Level>> fromIntegerId
    ) {
        this.toIntegerId = toIntegerId;
        this.fromIntegerId = fromIntegerId;
        toIntegerId.defaultReturnValue(MISSING_ID);
        maxId = toIntegerId.values().intStream().max().orElse(0);
    }
    
    public DimIntIdMap() {
        this(
            new Object2IntOpenHashMap<>(),
            new Int2ObjectOpenHashMap<>()
        );
    }
    
    public ResourceKey<Level> fromIntegerId(int integerId) {
        ResourceKey<Level> result = fromIntegerId.get(integerId);
        if (result == null) {
            throw new RuntimeException(
                "Missing Dimension " + integerId
            );
        }
        return result;
    }
    
    @Nullable
    public ResourceKey<Level> fromIntegerIdNullable(int integerId) {
        return fromIntegerId.get(integerId);
    }
    
    /**
     * Look up the integer id for {@code dim}. Unlike the old behavior, this
     * never throws for a dimension that simply wasn't known when the map
     * was built: it lazily assigns the next free id, remembers it (so
     * repeated calls are stable and existing ids are untouched), and flags
     * the map {@link #isDirty()} so the caller can re-sync clients. This
     * keeps a single unmapped dimension (e.g. AE2 spatial storage) from
     * crashing the server tick loop and bricking the world.
     */
    public int toIntegerId(ResourceKey<Level> dim) {
        int result = toIntegerId.getInt(dim);
        if (result == MISSING_ID) {
            int newId = getNextIntegerId();
            add(dim, newId);
            dirty = true;
            LOGGER.warn(
                "Dimension {} was missing from the dimension int id map. " +
                    "Lazily assigned it id {} instead of throwing. This usually means the " +
                    "dimension was created after this map was built (e.g. a dynamically " +
                    "created mod dimension like AE2 spatial storage, or a datapack dimension).",
                dim.location(), newId
            );
            return newId;
        }
        return result;
    }

    /**
     * True if {@link #toIntegerId(ResourceKey)} has lazily assigned an id
     * since the flag was last cleared via {@link #consumeDirty()}.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Returns whether the map was dirty, and clears the flag. Intended to
     * be called once by whichever caller is responsible for re-syncing the
     * map to clients after a lazy assignment.
     */
    public boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }
    
    public void add(ResourceKey<Level> dimId, int intId) {
        if (toIntegerId.containsKey(dimId)) {
            throw new RuntimeException(
                "Dimension Id Record already contains " + dimId.location() + " " + this
            );
        }
        if (fromIntegerId.containsKey(intId)) {
            throw new RuntimeException(
                "Dimension Id Record already contains " + intId + " " + this
            );
        }
        toIntegerId.put(dimId, intId);
        fromIntegerId.put(intId, dimId);
        maxId = Math.max(maxId, intId);
    }
    
    public boolean remove(ResourceKey<Level> dimId) {
        int intId = toIntegerId.removeInt(dimId);
        if (intId == MISSING_ID) {
            return false;
        }
        fromIntegerId.remove(intId);
        return true;
    }
    
    public boolean removeUnused(Set<ResourceKey<Level>> currentDimIds) {
        boolean changed = false;
        for (ResourceKey<Level> dimId : toIntegerId.keySet()) {
            if (!currentDimIds.contains(dimId)) {
                remove(dimId);
                changed = true;
            }
        }
        return changed;
    }
    
    public boolean containsDimId(ResourceKey<Level> dimId) {
        return toIntegerId.containsKey(dimId);
    }
    
    public boolean containsIntId(int intId) {
        return fromIntegerId.containsKey(intId);
    }
    
    public static DimIntIdMap fromTag(CompoundTag tag) {
        CompoundTag intids = tag.getCompound("intids");
        
        Object2IntOpenHashMap<ResourceKey<Level>> toIntegerId = new Object2IntOpenHashMap<>();
        Int2ObjectOpenHashMap<ResourceKey<Level>> fromIntegerId = new Int2ObjectOpenHashMap<>();
        
        intids.getAllKeys().forEach(dim -> {
            if (intids.contains(dim)) {
                int intid = intids.getInt(dim);
                ResourceKey<Level> dimId = Helper.dimIdToKey(dim);
                toIntegerId.put(dimId, intid);
                fromIntegerId.put(intid, dimId);
            }
        });
        
        return new DimIntIdMap(
            toIntegerId, fromIntegerId
        );
    }
    
    public CompoundTag toTag(Predicate<ResourceKey<Level>> filter) {
        CompoundTag intids = new CompoundTag();
        toIntegerId.forEach((key, intid) -> {
            if (filter.test(key)) {
                intids.put(key.location().toString(), IntTag.valueOf(intid));
            }
        });
        
        CompoundTag result = new CompoundTag();
        result.put("intids", intids);
        return result;
    }
    
    public Set<ResourceKey<Level>> getDimIdSet() {
        return Collections.unmodifiableSet(toIntegerId.keySet());
    }
    
    public int getNextIntegerId() {
        return maxId + 1;
    }
    
    @Override
    public String toString() {
        return toIntegerId.object2IntEntrySet().stream()
            .sorted(Comparator.comparingInt(e -> e.getIntValue()))
            .map(e -> e.getKey().location().toString() + " -> " + e.getIntValue())
            .collect(Collectors.joining("\n"));
    }
}
