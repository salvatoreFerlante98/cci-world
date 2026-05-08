package com.cciworld.generator;

import net.minecraft.resources.ResourceLocation;

/**
 * Outcome of {@link ClusterGenerator#decide} for a single chunk (cell-based v0.5.1).
 *
 * <p>{@code finalRecipe == null} means "no useful vein"; the chunk is either
 * outside its cell's cluster, the cell has no cluster (no_vein roll), or no
 * ring matched the cluster center distance. This corresponds to the safe
 * COE state {@code recipe=null, loaded=true} audited in COEVeinWriter.</p>
 */
public record ClusterDecision(
    int chunkX,
    int chunkZ,
    int distanceBlocks,
    ResourceLocation biome,
    // cell coords (cell-grid indices)
    int cellX,
    int cellZ,
    // cluster center and radius (in blocks); -1 / 0 if cell has no cluster
    int centerX,
    int centerZ,
    int radiusBlocks,
    int distanceFromCenterBlocks,
    ResourceLocation finalRecipe, // null = no-vein
    String reason,
    long clusterId,
    double roll
) {
    public boolean isNoVein() { return finalRecipe == null; }
}
