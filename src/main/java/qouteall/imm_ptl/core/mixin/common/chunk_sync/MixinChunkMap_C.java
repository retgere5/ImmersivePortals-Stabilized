package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.chunk_loading.PlayerChunkLoading;
import qouteall.imm_ptl.core.ducks.IEChunkMap;

/**
 * Cooperative chunk tracking:
 * <p>
 * Vanilla chunk tracking ({@link ChunkMap#applyChunkTrackingView}, {@link ChunkMap#onChunkReadyToSend},
 * {@link ChunkMap#getPlayers}) is left RUNNING so that per-player {@link ChunkTrackingView} state stays
 * correct for the player's current dimension. Other mods rely on that state via
 * {@link ChunkMap#isChunkTracked} / {@link ChunkMap#getPlayers} (e.g. Mekanism's block update
 * broadcasting); cancelling it caused those mods to never send update packets.
 * <p>
 * Immersive Portals still exclusively controls actual chunk data / unload packet sending:
 * the packet side effects of vanilla tracking all funnel through
 * {@link net.minecraft.server.network.PlayerChunkSender#markChunkPendingToSend} and
 * {@link net.minecraft.server.network.PlayerChunkSender#dropChunk}, which are no-op'ed in
 * {@link MixinPlayerChunkSender}. Chunk packets are sent on {@link PlayerChunkLoading}
 * (multi-dim, redirected) and unload packets on
 * {@link ImmPtlChunkTracking} purging. The only packet vanilla tracking still sends is
 * {@code ClientboundSetChunkCacheCenterPacket}, which the ImmPtl client chunk map ignores
 * ({@link qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap#updateViewCenter}).
 * <p>
 * Cross-dimension (portal-visible) chunk queries cannot be answered by the vanilla view; the
 * cross-dim viewer list is provided to {@link ChunkHolder#broadcastChanges} via the redirect in
 * {@link MixinChunkHolder} (packets there get dimension-redirected, which arbitrary
 * third-party packets could not be).
 */
@Mixin(value = ChunkMap.class, priority = 1100)
public abstract class MixinChunkMap_C implements IEChunkMap {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract ChunkHolder getVisibleChunkIfPresent(long long_1);

    @Shadow
    @Final
    private ThreadedLevelLightEngine lightEngine;

    @Shadow
    abstract int getPlayerViewDistance(ServerPlayer serverPlayer);

    @Override
    public int ip_getPlayerViewDistance(ServerPlayer player) {
        return getPlayerViewDistance(player);
    }

    @Override
    public ServerLevel ip_getWorld() {
        return level;
    }

    @Override
    public ThreadedLevelLightEngine ip_getLightingProvider() {
        return lightEngine;
    }

    @Override
    public ChunkHolder ip_getChunkHolder(long chunkPosLong) {
        return getVisibleChunkIfPresent(chunkPosLong);
    }

    /**
     * Not cancelled: the vanilla body only queues into the no-op'ed
     * {@link net.minecraft.server.network.PlayerChunkSender} and fires the ChunkWatch event
     * (which mods expect). Actual sending happens on {@link PlayerChunkLoading}.
     */
    @Inject(
        method = "onChunkReadyToSend",
        at = @At("HEAD")
    )
    private void onOnChunkReadyToSend(LevelChunk chunk, CallbackInfo ci) {
        ImmPtlChunkTracking.onChunkProvidedDeferred(chunk);
    }
}
