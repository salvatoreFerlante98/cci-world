package com.cciworld.generator;

import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
import com.cciworld.policy.PolicyChunkKey;
import com.cciworld.policy.PolicyQueueEntry;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative cluster generator engine (CCI World v0.5).
 *
 * <p>Lifecycle:</p>
 * <ul>
 *   <li>{@link #onChunkLoad(ChunkEvent.Load)} only enqueues a chunk key — no
 *       heavy work is done in the chunk-load event.</li>
 *   <li>{@link #onServerTickPost(ServerTickEvent.Post)} drains up to
 *       {@code policy_chunks_per_tick} entries; for each one it calls
 *       {@link ClusterGenerator#decide} and writes the final OreData.</li>
 * </ul>
 *
 * <p>Hard rules respected: no mixin, no reflection, no COE fork, no forced
 * chunk generation ({@code getChunkNow}), no heavy work in ChunkLoadEvent.</p>
 */
public final class ClusterGeneratorEngine {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Session cache: chunk keys whose authoritative decision has already been written. */
    private static final Set<PolicyChunkKey> SESSION_CACHE = new HashSet<>();

    private static int totalProcessed   = 0;
    private static int totalNoVein      = 0;
    private static int totalWritten     = 0;
    private static int totalSkippedUnloaded = 0;
    private static int totalSkippedCached   = 0;

    private static final Map<ResourceLocation, Integer> WRITES_BY_RESOURCE = new LinkedHashMap<>();

    private ClusterGeneratorEngine() {}

    // -- public accessors -----------------------------------------------------

    public static int getQueueSize()             { return ClusterQueue.size(); }
    public static int getSessionCacheSize()      { return SESSION_CACHE.size(); }
    public static int getTotalProcessed()        { return totalProcessed; }
    public static int getTotalNoVein()           { return totalNoVein; }
    public static int getTotalWritten()          { return totalWritten; }
    public static int getTotalSkippedUnloaded()  { return totalSkippedUnloaded; }
    public static int getTotalSkippedCached()    { return totalSkippedCached; }

    public static Map<ResourceLocation, Integer> getWritesByResource() {
        return new HashMap<>(WRITES_BY_RESOURCE);
    }

    public static int clearSessionCache() {
        int before = SESSION_CACHE.size();
        SESSION_CACHE.clear();
        return before;
    }

    public static boolean isCached(PolicyChunkKey key) {
        return SESSION_CACHE.contains(key);
    }

    /** Marks a chunk as already authoritatively written this session. */
    public static void markCached(PolicyChunkKey key) {
        SESSION_CACHE.add(key);
    }

    // -- event hooks ---------------------------------------------------------

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!CCIWorldConfig.ENABLED.get()) return;
        if (!CCIWorldConfig.AUTHORITATIVE_GENERATION_ENABLED.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        PolicyChunkKey key = new PolicyChunkKey(level.dimension(), cx, cz);

        if (SESSION_CACHE.contains(key)) return;

        if (ClusterQueue.size() >= CCIWorldConfig.MAX_PENDING_GEN_JOBS.get()) {
            LOGGER.debug("[CCI World][gen] queue full ({}), dropping [{}/{}]",
                ClusterQueue.size(), cx, cz);
            return;
        }

        ClusterQueue.enqueue(new PolicyQueueEntry(level.dimension(), cx, cz, "chunk_load", "system"));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!CCIWorldConfig.ENABLED.get()) return;
        if (!CCIWorldConfig.AUTHORITATIVE_GENERATION_ENABLED.get()) return;
        if (ClusterQueue.isEmpty()) return;

        MinecraftServer server = event.getServer();
        int maxPerTick = CCIWorldConfig.GEN_CHUNKS_PER_TICK.get();
        int processed = 0;

        while (processed < maxPerTick && !ClusterQueue.isEmpty()) {
            PolicyQueueEntry entry = ClusterQueue.poll();
            if (entry == null) break;
            processed++;

            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null) { totalSkippedUnloaded++; continue; }

            // Non-generating lookup; null if the chunk unloaded since enqueue.
            LevelChunk chunk = level.getChunkSource().getChunkNow(entry.chunkX(), entry.chunkZ());
            if (chunk == null) { totalSkippedUnloaded++; continue; }

            PolicyChunkKey key = entry.key();
            if (SESSION_CACHE.contains(key)) { totalSkippedCached++; continue; }

            try {
                ClusterDecision decision = ClusterGenerator.decide(level, chunk);
                writeDecision(chunk, decision);
                SESSION_CACHE.add(key);
                totalProcessed++;
                if (decision.isNoVein()) {
                    totalNoVein++;
                } else {
                    totalWritten++;
                    WRITES_BY_RESOURCE.merge(decision.finalRecipe(), 1, Integer::sum);
                }
            } catch (Throwable t) {
                LOGGER.error("[CCI World][gen] error on [{}/{}]: {}",
                    entry.chunkX(), entry.chunkZ(), t.toString());
            }
        }
    }

    /** Writes the authoritative OreData state implied by {@code decision}. */
    public static void writeDecision(LevelChunk chunk, ClusterDecision decision) {
        if (decision.isNoVein()) {
            COEVeinWriter.writeNoVein(chunk);
        } else {
            COEVeinWriter.writeVein(chunk, decision.finalRecipe(),
                (float) CCIWorldConfig.RANDOM_MULTIPLIER.get().doubleValue()); // Double -> float
        }
    }
}
