package com.cciworld.policy;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One entry in the PolicyQueue.
 * source: "auto" (from AutomaticPolicyEngine scan) | "manual" (from player command)
 * requester: player name, may be null for automated entries with no specific player
 */
public record PolicyQueueEntry(
    ResourceKey<Level> dimension,
    int chunkX,
    int chunkZ,
    String source,
    String requester
) {
    public PolicyChunkKey key() {
        return new PolicyChunkKey(dimension, chunkX, chunkZ);
    }
}
