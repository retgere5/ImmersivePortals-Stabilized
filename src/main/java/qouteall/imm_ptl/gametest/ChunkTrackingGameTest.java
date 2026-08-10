package qouteall.imm_ptl.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;
import java.util.UUID;

/**
 * Regression test for cooperative chunk tracking
 * ({@code qouteall.imm_ptl.core.mixin.common.chunk_sync.MixinChunkMap_C}).
 * <p>
 * Historically ImmPtl cancelled {@code ChunkMap.applyChunkTrackingView} at HEAD, so the vanilla
 * per-player {@code ChunkTrackingView} stayed permanently EMPTY. That made
 * {@code ChunkMap.isChunkTracked} always false, which silently disabled block-update packet
 * broadcasting in mods that consult vanilla tracking (Mekanism class of issues: fork issues
 * #63/#65/#29). This test joins a real {@link ServerPlayer} (with a live connection, so the
 * whole {@code placeNewPlayer -> addEntity -> updatePlayerStatus -> updateChunkTracking ->
 * applyChunkTrackingView} chain runs) and asserts the vanilla view is populated for the
 * player's own chunk, i.e. the state that {@code isChunkTracked} reads (the join runs the
 * whole chain because {@code placeNewPlayer} synchronously adds the player entity):
 * <ul>
 *   <li>{@code player.getChunkTrackingView().contains(ownChunk)} is true
 *       (was false before the fix),</li>
 *   <li>{@code chunkSender.isPending(ownChunk)} is false (ImmPtl keeps vanilla's pending set
 *       empty by no-op'ing {@code markChunkPendingToSend}),</li>
 *   <li>vanilla {@code ChunkMap.getPlayers} (no longer @Overwrite'n) reports the player as
 *       tracking its own chunk.</li>
 * </ul>
 */
@GameTestHolder("immersive_portals_core")
public class ChunkTrackingGameTest {
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void vanillaChunkTrackingViewStaysPopulated(GameTestHelper helper) {
        ServerPlayer player = joinMockServerPlayer(helper);
        try {
            ServerLevel level = player.serverLevel();
            ChunkPos ownChunk = player.chunkPosition();

            helper.assertTrue(
                player.getChunkTrackingView().contains(ownChunk.x, ownChunk.z),
                "vanilla ChunkTrackingView does not contain the player's own chunk"
                    + " (applyChunkTrackingView suppressed?)"
            );

            helper.assertFalse(
                player.connection.chunkSender.isPending(ownChunk.toLong()),
                "vanilla PlayerChunkSender pending set is not empty"
            );

            List<ServerPlayer> tracking =
                level.getChunkSource().chunkMap.getPlayers(ownChunk, false);
            helper.assertTrue(
                tracking.contains(player),
                "vanilla ChunkMap.getPlayers does not report the player tracking its own chunk"
            );
        }
        finally {
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Same as the deprecated {@link GameTestHelper#makeMockServerPlayerInLevel()}, plus
     * {@link NetworkRegistry#configureMockConnection(Connection)} so that NeoForge treats the
     * mock connection as a fully negotiated modded connection. Without that, the modded
     * payloads ImmPtl sends during {@code placeNewPlayer} (e.g. {@code imm_ptl:dim_int_id_sync})
     * are rejected with "Payload ... may not be sent to the client!".
     */
    private static ServerPlayer joinMockServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "test-mock-player"), false
        );
        ServerPlayer player = new ServerPlayer(
            helper.getLevel().getServer(), helper.getLevel(),
            cookie.gameProfile(), cookie.clientInformation()
        ) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
