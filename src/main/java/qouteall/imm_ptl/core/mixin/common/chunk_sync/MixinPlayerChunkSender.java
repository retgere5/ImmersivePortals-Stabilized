package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;

/**
 * Disable the functionality of this class.
 * Because its implementation is based on single-dimension loaded and near-loading-only assumption.
 * <p>
 * NOTE these no-ops are load-bearing for cooperative chunk tracking
 * ({@link qouteall.imm_ptl.core.mixin.common.chunk_sync.MixinChunkMap_C}):
 * vanilla {@link net.minecraft.server.level.ChunkMap#applyChunkTrackingView} /
 * {@link net.minecraft.server.level.ChunkMap#onChunkReadyToSend} run un-cancelled and their
 * packet side effects all funnel through {@link PlayerChunkSender#markChunkPendingToSend} and
 * {@link PlayerChunkSender#dropChunk}. Keeping both no-op means vanilla tracking updates the
 * per-player {@link net.minecraft.server.level.ChunkTrackingView} without sending any chunk
 * data or unload packets (ImmPtl sends those itself, multi-dim and redirected).
 * It also keeps {@code pendingChunks} permanently empty, so the un-mixined vanilla
 * {@link PlayerChunkSender#isPending} naturally returns false and
 * {@link net.minecraft.server.level.ChunkMap#isChunkTracked} reduces to a tracking-view check.
 */
@SuppressWarnings({"JavadocReference", "UnstableApiUsage"})
@Mixin(value = PlayerChunkSender.class)
public class MixinPlayerChunkSender {

    /**
     * @author qouteall
     * @reason see class comment
     */
    @Overwrite
    public void markChunkPendingToSend(LevelChunk levelChunk) {
    
    }
    
    /**
     * @author qouteall
     * @reason see class comment
     */
    @Overwrite
    public void dropChunk(ServerPlayer serverPlayer, ChunkPos chunkPos) {
    
    }
    
    /**
     * Fabric API mixins this method
     * {@link net.fabricmc.fabric.mixin.attachment.ChunkDataSenderMixin}
     */
    @Inject(
        method = "sendNextChunks",
        at = @At("HEAD"),
        cancellable = true
    )
    public void sendNextChunks(ServerPlayer serverPlayer, CallbackInfo ci) {
        ImmPtlChunkTracking.getPlayerInfo(serverPlayer).doChunkSending(serverPlayer);
        ci.cancel();
    }
    
    /**
     * @author qouteall
     * @reason see class comment
     */
    @Overwrite
    public void onChunkBatchReceivedByClient(float f) {

    }
}
