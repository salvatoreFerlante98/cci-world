package com.cciworld.policy;

import com.cciworld.config.CCIWorldConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AutomaticPolicyEngine {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Session state — reset by clearSessionCache()
    private static final Set<PolicyChunkKey> SESSION_CACHE = new HashSet<>();
    private static int totalApplied  = 0;
    private static int totalSkipped  = 0;

    // Scan phase state
    private static int tickCounter = 0;

    private AutomaticPolicyEngine() {}

    // -------------------------------------------------------------------------
    // Public accessors for /cci_world policy_status and clear_policy_session_cache
    // -------------------------------------------------------------------------

    public static int getSessionCacheSize()      { return SESSION_CACHE.size(); }
    public static int getTotalApplied()          { return totalApplied; }
    public static int getTotalSkipped()          { return totalSkipped; }
    public static int getQueueSize()             { return PolicyQueue.size(); }
    public static int getWarnedInvalidBiomeIds() { return BiomePolicyService.getWarnedInvalidCount(); }

    public static int clearSessionCache() {
        int before = SESSION_CACHE.size();
        SESSION_CACHE.clear();
        return before;
    }

    // -------------------------------------------------------------------------
    // Server tick entry point
    // -------------------------------------------------------------------------

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!CCIWorldConfig.ENABLED.get()) return;

        MinecraftServer server = event.getServer();

        // Phase 1 — periodic scan of player-surrounding loaded chunks
        tickCounter++;
        if (tickCounter >= CCIWorldConfig.SCAN_INTERVAL_TICKS.get()) {
            tickCounter = 0;
            if (CCIWorldConfig.AUTOMATIC_POLICY_ENABLED.get()) {
                scanPlayers(server);
            }
        }

        // Phase 2 — process up to maxChunksPerTick from queue
        processQueue(server);
    }

    // -------------------------------------------------------------------------
    // Scan phase: iterate players, enqueue loaded chunks not yet in session cache
    // -------------------------------------------------------------------------

    private static void scanPlayers(MinecraftServer server) {
        int radius = CCIWorldConfig.PLAYER_SCAN_RADIUS_CHUNKS.get();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        int candidates    = 0;
        int enqueued      = 0;
        int skippedCache  = 0;
        int skippedUnloaded = 0;

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

                    if (SESSION_CACHE.contains(key)) {
                        skippedCache++;
                        continue;
                    }

                    // getChunkNow — guaranteed non-generating: returns null if chunk is not
                    // already in memory. We never call getChunk/getChunkAt here.
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        skippedUnloaded++;
                        continue;
                    }

                    boolean added = PolicyQueue.enqueue(new PolicyQueueEntry(
                        level.dimension(), cx, cz, "auto", player.getName().getString()
                    ));
                    if (added) enqueued++;
                }
            }
        }

        LOGGER.debug(
            "[CCI World] scan: candidates={} enqueued={} skip_cache={} skip_unloaded={} queue={}",
            candidates, enqueued, skippedCache, skippedUnloaded, PolicyQueue.size()
        );
    }

    // -------------------------------------------------------------------------
    // Process phase: dequeue up to maxChunksPerTick, apply policy
    // -------------------------------------------------------------------------

    private static void processQueue(MinecraftServer server) {
        if (PolicyQueue.isEmpty()) return;

        int maxPerTick = CCIWorldConfig.MAX_CHUNKS_PER_TICK.get();
        int processed  = 0;
        int applied    = 0;
        int skipped    = 0;

        while (processed < maxPerTick && !PolicyQueue.isEmpty()) {
            PolicyQueueEntry entry = PolicyQueue.poll();
            if (entry == null) break;
            processed++;

            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null) {
                skipped++;
                totalSkipped++;
                continue;
            }

            // getChunkNow — chunk may have been unloaded between enqueue and processing;
            // returns null without generating. Skip silently if no longer loaded.
            LevelChunk chunk = level.getChunkSource().getChunkNow(entry.chunkX(), entry.chunkZ());
            if (chunk == null) {
                skipped++;
                totalSkipped++;
                continue;
            }

            PolicyResult result = ResourcePolicyService.apply(level, chunk);

            // Mark processed in session cache regardless of outcome so we don't rescan.
            SESSION_CACHE.add(entry.key());

            if (result.applied()) {
                applied++;
                totalApplied++;
                LOGGER.debug(
                    "[CCI World] applied [{}/{}] {} -> {} policy={} src={}",
                    entry.chunkX(), entry.chunkZ(),
                    result.previousRecipe() != null ? result.previousRecipe().getPath() : "none",
                    result.newRecipe()      != null ? result.newRecipe().getPath()      : "-",
                    result.policyType(),
                    entry.source()
                );
            } else {
                skipped++;
                totalSkipped++;
                LOGGER.debug(
                    "[CCI World] skip [{}/{}] reason={} src={}",
                    entry.chunkX(), entry.chunkZ(), result.reason(), entry.source()
                );
            }
        }

        if (processed > 0) {
            LOGGER.debug(
                "[CCI World] batch: processed={} applied={} skipped={} remaining={}",
                processed, applied, skipped, PolicyQueue.size()
            );
        }
    }
}
