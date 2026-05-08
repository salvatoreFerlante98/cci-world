package com.cciworld.policy;

import com.cciworld.config.CCIWorldConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AutomaticPolicyEngine {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Session cache — prevents reprocessing chunks already handled this session
    private static final Set<PolicyChunkKey> SESSION_CACHE = new HashSet<>();

    // Counters — lifetime totals, reset only by clearSessionCache()
    private static int totalApplied           = 0;
    private static int totalSkippedUnloaded   = 0;  // level or chunk gone before processing
    private static int totalSkippedCached     = 0;  // already in session cache
    private static int totalSkippedNoAction   = 0;  // processed but policy returned no replacement

    // Per-resource replacement counts (newRecipe -> count)
    private static final Map<ResourceLocation, Integer> REPLACEMENTS_BY_RESOURCE = new LinkedHashMap<>();

    // Periodic player-scan phase state
    private static int tickCounter = 0;

    // One-shot warning when both engines are simultaneously enabled
    private static boolean conflictWarned = false;

    private AutomaticPolicyEngine() {}

    /** Returns true if the v0.4 engine must stand down (authoritative gen owns OreData). */
    private static boolean isSuppressedByAuthoritative() {
        boolean auth = CCIWorldConfig.AUTHORITATIVE_GENERATION_ENABLED.get();
        boolean policy = CCIWorldConfig.POLICY_ENGINE_ENABLED.get();
        if (auth && policy && !conflictWarned) {
            LOGGER.warn("[CCI World] CONFIG CONFLICT: both authoritative_generation_enabled=true and " +
                "policy_engine_enabled=true. v0.5 authoritative generator is the single OreData writer; " +
                "v0.4 policy engine is suppressed. Disable policy_engine_enabled to silence this warning.");
            conflictWarned = true;
        }
        return auth;
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    public static int getSessionCacheSize()      { return SESSION_CACHE.size(); }
    public static int getTotalApplied()          { return totalApplied; }
    public static int getTotalSkipped()          { return totalSkippedUnloaded + totalSkippedCached + totalSkippedNoAction; }
    public static int getTotalSkippedUnloaded()  { return totalSkippedUnloaded; }
    public static int getTotalSkippedCached()    { return totalSkippedCached; }
    public static int getTotalSkippedNoAction()  { return totalSkippedNoAction; }
    public static int getQueueSize()             { return PolicyQueue.size(); }
    public static int getWarnedInvalidBiomeIds() { return BiomePolicyService.getWarnedInvalidCount(); }

    public static Map<ResourceLocation, Integer> getReplacementsByResource() {
        return Collections.unmodifiableMap(REPLACEMENTS_BY_RESOURCE);
    }

    public static int clearSessionCache() {
        int before = SESSION_CACHE.size();
        SESSION_CACHE.clear();
        return before;
    }

    // -------------------------------------------------------------------------
    // ChunkLoadEvent handler (v0.4 engine) — enqueues every freshly loaded chunk
    // -------------------------------------------------------------------------

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!CCIWorldConfig.ENABLED.get()) return;
        if (!CCIWorldConfig.POLICY_ENGINE_ENABLED.get()) return;
        if (isSuppressedByAuthoritative()) return; // v0.5 owns OreData
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        PolicyChunkKey key = new PolicyChunkKey(level.dimension(), cx, cz);

        // Skip if already handled this session
        if (CCIWorldConfig.PROCESSED_CACHE_ENABLED.get() && SESSION_CACHE.contains(key)) return;

        // Drop if queue is at capacity
        if (PolicyQueue.size() >= CCIWorldConfig.MAX_PENDING_POLICY_JOBS.get()) {
            LOGGER.debug("[CCI World] queue full ({}) — dropping chunk [{}/{}]",
                PolicyQueue.size(), cx, cz);
            return;
        }

        PolicyQueue.enqueue(new PolicyQueueEntry(level.dimension(), cx, cz, "chunk_load", "system"));
    }

    // -------------------------------------------------------------------------
    // Server tick entry point
    // -------------------------------------------------------------------------

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!CCIWorldConfig.ENABLED.get()) return;
        if (isSuppressedByAuthoritative()) return; // v0.5 owns OreData; v0.4 stands down

        MinecraftServer server = event.getServer();

        // Periodic player-scan phase (legacy fallback — covers chunks loaded before the event was active)
        tickCounter++;
        if (tickCounter >= CCIWorldConfig.SCAN_INTERVAL_TICKS.get()) {
            tickCounter = 0;
            if (CCIWorldConfig.AUTOMATIC_POLICY_ENABLED.get()) {
                scanPlayers(server);
            }
        }

        // Process queue
        processQueue(server);
    }

    // -------------------------------------------------------------------------
    // Player-scan phase: enqueue loaded chunks near each player not yet cached
    // -------------------------------------------------------------------------

    private static void scanPlayers(MinecraftServer server) {
        int radius = CCIWorldConfig.PLAYER_SCAN_RADIUS_CHUNKS.get();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        int candidates = 0, enqueued = 0, skippedCache = 0, skippedUnloaded = 0;

        for (ServerPlayer player : players) {
            ServerLevel level = player.serverLevel();
            int playerCX = SectionPos.blockToSectionCoord(player.blockPosition().getX());
            int playerCZ = SectionPos.blockToSectionCoord(player.blockPosition().getZ());

            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    candidates++;
                    int cx = playerCX + dx;
                    int cz = playerCZ + dz;
                    PolicyChunkKey key = new PolicyChunkKey(level.dimension(), cx, cz);

                    if (SESSION_CACHE.contains(key)) { skippedCache++; continue; }

                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) { skippedUnloaded++; continue; }

                    if (PolicyQueue.size() < CCIWorldConfig.MAX_PENDING_POLICY_JOBS.get()) {
                        boolean added = PolicyQueue.enqueue(new PolicyQueueEntry(
                            level.dimension(), cx, cz, "auto", player.getName().getString()
                        ));
                        if (added) enqueued++;
                    }
                }
            }
        }

        LOGGER.debug("[CCI World] scan: candidates={} enqueued={} skip_cache={} skip_unloaded={} queue={}",
            candidates, enqueued, skippedCache, skippedUnloaded, PolicyQueue.size());
    }

    // -------------------------------------------------------------------------
    // Process phase: drain up to policy_chunks_per_tick entries from the queue
    // -------------------------------------------------------------------------

    private static void processQueue(MinecraftServer server) {
        if (PolicyQueue.isEmpty()) return;

        int maxPerTick = CCIWorldConfig.POLICY_CHUNKS_PER_TICK.get();
        int processed = 0, applied = 0, skipped = 0;

        while (processed < maxPerTick && !PolicyQueue.isEmpty()) {
            PolicyQueueEntry entry = PolicyQueue.poll();
            if (entry == null) break;
            processed++;

            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null) {
                skipped++;
                totalSkippedUnloaded++;
                continue;
            }

            // getChunkNow — non-generating; null if chunk unloaded since enqueue
            LevelChunk chunk = level.getChunkSource().getChunkNow(entry.chunkX(), entry.chunkZ());
            if (chunk == null) {
                skipped++;
                totalSkippedUnloaded++;
                continue;
            }

            PolicyChunkKey key = entry.key();

            // Double-check session cache (handles re-enqueue after cache clear)
            if (CCIWorldConfig.PROCESSED_CACHE_ENABLED.get() && SESSION_CACHE.contains(key)) {
                skipped++;
                totalSkippedCached++;
                continue;
            }

            PolicyResult result = ResourcePolicyService.apply(level, chunk);

            // Mark processed so neither scan phase nor ChunkLoadEvent re-enqueues it
            if (CCIWorldConfig.PROCESSED_CACHE_ENABLED.get()) {
                SESSION_CACHE.add(key);
            }

            if (result.applied()) {
                applied++;
                totalApplied++;
                if (result.newRecipe() != null) {
                    REPLACEMENTS_BY_RESOURCE.merge(result.newRecipe(), 1, Integer::sum);
                }
                LOGGER.debug("[CCI World] applied [{}/{}] {} -> {} policy={} src={}",
                    entry.chunkX(), entry.chunkZ(),
                    result.previousRecipe() != null ? result.previousRecipe().getPath() : "none",
                    result.newRecipe()      != null ? result.newRecipe().getPath()      : "-",
                    result.policyType(), entry.source());
            } else {
                skipped++;
                totalSkippedNoAction++;
                LOGGER.debug("[CCI World] skip [{}/{}] reason={} src={}",
                    entry.chunkX(), entry.chunkZ(), result.reason(), entry.source());
            }
        }

        if (processed > 0) {
            LOGGER.debug("[CCI World] batch: processed={} applied={} skipped={} remaining={}",
                processed, applied, skipped, PolicyQueue.size());
        }
    }
}
