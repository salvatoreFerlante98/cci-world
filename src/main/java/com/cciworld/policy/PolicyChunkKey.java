package com.cciworld.policy;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Identifies a chunk uniquely across dimensions. Used as session-cache and in-queue dedup key. */
public record PolicyChunkKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {}
