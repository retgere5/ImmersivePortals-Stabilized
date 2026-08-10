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
import java.util.List;

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

    /**
     * Thread-hardening for #54's off-thread lazy-assignment path
     * ({@link #serverDimKeyToInt}): {@code withForceRedirectAndGet} only
     * logs a warning on a server-thread mismatch, it does not prevent one,
     * so this can be reached from e.g. a ForkJoinWorkerThread doing packet
     * redirection. Without protection, this method's iteration of
     * {@code server.getPlayerList().getPlayers()} (and, transitively via
     * {@code DimIdSyncPacket.createFromServer}, {@code server.getAllLevels()})
     * races the server thread's own mutation of those same live collections
     * (players joining/leaving, levels loading/unloading) -- a
     * ConcurrentModificationException there would abort the broadcast loop
     * partway through, silently leaving the remaining players on a stale
     * client-side map. Such a player then throws on the next packet that
     * references the new dimension id ({@code DimIntIdMap#fromIntegerId} on
     * the client) -- the exact crash class #54 closed on the server side,
     * reopened here through a different door.
     * <p>
     * On-thread callers stay synchronous rather than also hopping through
     * {@code server.execute}: the wrapped packet that references the new id
     * (built by {@code PacketRedirection.createRedirectedMessage} right
     * after this method returns) must reach the client's connection after
     * the sync packet. Both packets are handed to the same per-connection
     * Netty event loop via {@code Connection.send}; when both
     * {@code ServerCommonPacketListenerImpl.send} calls happen back-to-back
     * on the same calling thread, they're submitted to that event loop in
     * that order and Netty preserves FIFO order, so staying synchronous here
     * is what keeps the ordering intact.
     * <p>
     * The off-thread hop below does NOT carry the same ordering guarantee,
     * and that should be stated honestly rather than assumed: the redirected
     * packet's own {@code Connection.send} runs synchronously on the calling
     * (off) thread immediately after this method returns, so it can be
     * submitted to the Netty event loop before the {@code server.execute}
     * task below even runs. For that one player's connection, the redirected
     * packet can beat the sync packet to the wire. The failure mode if that
     * race is lost is the same "Missing Dimension" client-side throw
     * described above, but narrowed to a single player/dimension instead of
     * the whole broadcast -- a bounded, loud (logged/thrown, not silent)
     * failure that is an acceptable trade for eliminating the broadcast-wide
     * CME/skip hazard this method exists to fix. {@code createPacket}'s
     * {@code server.getAllLevels()} iteration is covered by the same hop,
     * since it happens inside this method's call frame.
     */
    private static void syncDimIdMapToPlayers(MinecraftServer server) {
        if (!server.isSameThread()) {
            server.execute(() -> syncDimIdMapToPlayers(server));
            return;
        }

        var packet = MiscNetworking.DimIdSyncPacket.createPacket(server);

        // Snapshot before iterating: even on-thread, sending to one
        // player's connection can synchronously trigger disconnect handling
        // that mutates the player list, which would otherwise throw
        // ConcurrentModificationException mid-broadcast.
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            player.connection.send(packet);
        }
    }
}
