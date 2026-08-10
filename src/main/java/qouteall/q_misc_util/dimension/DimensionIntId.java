package qouteall.q_misc_util.dimension;

import com.mojang.logging.LogUtils;
import de.nick1st.imm_ptl.events.ClientExitEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.MiscNetworking;

import java.util.HashSet;

public class DimensionIntId {
    private static final Logger LOGGER = LogUtils.getLogger();
//    public static final ResourceLocation DYNAMIC_UPDATE_EVENT_EARLY_PHASE =
//        McHelper.newResourceLocation("iportal:early_phase");
    
    public static DimIntIdMap clientRecord;
    
    public static void init() {
        // make sure that dimension int id updates before global portal storage update
 //       DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.addPhaseOrdering(
 //           DYNAMIC_UPDATE_EVENT_EARLY_PHASE,
 //           Event.DEFAULT_PHASE
 //       );
 //
 //       DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.register(
 //           DYNAMIC_UPDATE_EVENT_EARLY_PHASE,
 //           (server, dimensions) -> {
 //               onServerDimensionChanged(server);
 //           }
 //       );
    }
    
    //@Environment(EnvType.CLIENT)
    public static void initClient() {
        NeoForge.EVENT_BUS.addListener(ClientExitEvent.class, (e) -> DimensionIntId.onClientExit());
    }
    
    //@Environment(EnvType.CLIENT)
    private static void onClientExit() {
        clientRecord = null;
    }

    /**
     * Note this should not be used in networking thread.
     */
    //@Environment(EnvType.CLIENT)
    public static @NotNull DimIntIdMap getClientMap() {
        Validate.notNull(clientRecord,
            "Client dim id record is not yet synced. This should not be used in networking thread."
        );
        return clientRecord;
    }
    
    public static @NotNull DimIntIdMap getServerMap(MinecraftServer server) {
        IPPerServerInfo perServerInfo = IPPerServerInfo.of(server);
        DimIntIdMap rec = perServerInfo.dimIntIdMap;
        Validate.notNull(rec, "Server dim id record is not yet initialized");
        return rec;
    }

    /**
     * Resolve {@code dimension} to its integer id in the server's map. This
     * is the sole server-side entry point used by packet redirection (see
     * PortalAPI#serverDimKeyToInt), so it is the general fix point for #54:
     * DimIntIdMap#toIntegerId no longer throws when the dimension is
     * missing -- it lazily assigns an id instead -- and here, if that
     * happened, we immediately re-sync the updated map to every connected
     * player using the same DimIdSyncPacket mechanism that
     * onServerDimensionChanged already uses. This runs before the packet
     * that triggered the lookup is sent (createRedirectedMessage sends the
     * sync packet, then returns the wrapped packet for the caller to send),
     * so clients learn about the new dimension id before they can receive
     * any packet referencing it.
     */
    public static int serverDimKeyToInt(MinecraftServer server, ResourceKey<Level> dimension) {
        DimIntIdMap map = getServerMap(server);
        int intId = map.toIntegerId(dimension);

        if (map.consumeDirty()) {
            LOGGER.info(
                "Dimension int id map was updated by lazy assignment for {} (id {}). Re-syncing to clients.",
                dimension.location(), intId
            );
            syncDimIdMapToPlayers(server);
        }

        return intId;
    }

    public static void onServerStarted(MinecraftServer server) {
        DimIntIdMap rec = new DimIntIdMap();

        fillInVanillaDimIds(rec);
        fillInLoadedDimIds(rec, server);

        IPPerServerInfo perServerInfo = IPPerServerInfo.of(server);
        perServerInfo.dimIntIdMap = rec;
        LOGGER.info("Server dimension integer id mapping:\n{}", rec);
    }

    /**
     * Guarantees the historical ids (0 / -1 / 1) for overworld / nether /
     * end regardless of dimension registration order.
     */
    private static void fillInVanillaDimIds(DimIntIdMap rec) {
        if (!rec.containsDimId(Level.OVERWORLD)) {
            rec.add(Level.OVERWORLD, 0);
        }
        if (!rec.containsDimId(Level.NETHER)) {
            rec.add(Level.NETHER, -1);
        }
        if (!rec.containsDimId(Level.END)) {
            rec.add(Level.END, 1);
        }
    }

    /**
     * Generalizes the community's single-dimension patch (an @Inject at the
     * tail of fillInVanillaDimIds that hand-added one hardcoded mod
     * dimension, e.g. ae2:spatial_storage): register EVERY dimension the
     * server has loaded at boot time -- vanilla, datapack, and mod
     * dimensions alike -- instead of hand-picking specific ids.
     * <p>
     * This only covers dimensions that already exist as a ServerLevel at
     * boot. Dimensions created dynamically *after* boot (e.g. AE2's spatial
     * storage level, which AE2 creates lazily the first time a spatial cell
     * is used, not at world load) are not visible here. There used to be a
     * DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT hook for that case
     * (see the commented-out registration in {@link #init()} and
     * {@link #onServerDimensionChanged}), but that event does not exist in
     * this NeoForge port anymore (see {@code DimensionEvents}), which is
     * exactly how a fresh AE2 spatial storage dimension used to fall
     * through the cracks and crash the server tick loop. That runtime gap
     * is covered by the lazy assignment + re-sync in
     * {@link DimIntIdMap#toIntegerId} / {@link #serverDimKeyToInt} instead.
     */
    private static void fillInLoadedDimIds(DimIntIdMap rec, MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            ResourceKey<Level> dimId = world.dimension();
            if (!rec.containsDimId(dimId)) {
                rec.add(dimId, rec.getNextIntegerId());
            }
        }
    }

    /**
     * Currently unreachable: the event registration that would call this
     * (see the commented-out block in {@link #init()}) targeted a dynamic
     * dimension event that no longer exists in this NeoForge port. Kept
     * around (and still correct) in case a suitable hook is reintroduced;
     * for now, {@link #serverDimKeyToInt} is what actually keeps the map
     * (and its client sync) up to date at runtime.
     */
    public static void onServerDimensionChanged(MinecraftServer server) {
        DimIntIdMap map = getServerMap(server);

        for (ResourceKey<Level> levelKey : server.levelKeys()) {
            if (!map.containsDimId(levelKey)) {
                map.add(levelKey, map.getNextIntegerId());
            }
        }

        HashSet<ResourceKey<Level>> usedDimKeys = new HashSet<>(server.levelKeys());

        // avoid vanilla dimensions from being removed from mapping
        usedDimKeys.add(Level.OVERWORLD);
        usedDimKeys.add(Level.NETHER);
        usedDimKeys.add(Level.END);

        map.removeUnused(usedDimKeys);

        LOGGER.info("Current dimension integer id mapping:\n{}", map);

        syncDimIdMapToPlayers(server);
    }

    private static void syncDimIdMapToPlayers(MinecraftServer server) {
        var packet = MiscNetworking.DimIdSyncPacket.createPacket(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}
