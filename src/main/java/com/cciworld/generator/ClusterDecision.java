package com.cciworld.generator;

import net.minecraft.resources.ResourceLocation;

/**
 * Outcome of {@link ClusterGenerator#decide} for a single chunk (cell-based).
 *
 * <p>{@code finalRecipe == null} means "no useful vein". The exact reason is
 * captured by {@link #reason}:</p>
 * <ul>
 *   <li>{@code no_vein_cell_roll} — base no-vein roll (pre-weighted-pick) hit.</li>
 *   <li>{@code weighted_empty:&lt;band&gt;} — weighted EMPTY candidate won the
 *       per-cell weighted pick (v0.7.1). {@code band} is one of
 *       {@code near}/{@code mid}/{@code far} based on the cluster center's
 *       distance from spawn.</li>
 *   <li>{@code out_of_all_rings} — no resource ring matched the cluster
 *       center distance.</li>
 *   <li>{@code outside_cluster_radius:&lt;alias&gt;} — the cell has a cluster
 *       but this chunk is outside its radius.</li>
 *   <li>{@code in_cluster:&lt;alias&gt;} — the chunk is inside the cluster
 *       (a real recipe is set).</li>
 * </ul>
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
    double roll,
    // v0.7.1 — weighted-empty diagnostics (0/empty when not applicable)
    double baseNoVeinRoll,
    double weightedRollPick,
    int    weightedTotalWeight,
    int    emptyWeight,
    boolean weightedPickedEmpty,
    String  emptyBand, // "near" | "mid" | "far" | "" when not evaluated
    // v0.8 — biome rule diagnostics (evaluated only when a Holder<Biome> is available)
    boolean biomeEvaluated,
    boolean biomeAllowed,
    String  biomeReason,         // e.g. biome_allowed_no_match | biome_denied:<tag> | biome_not_evaluated
    java.util.List<String> biomeMatchedAllowed,
    java.util.List<String> biomeMatchedDenied,
    String  pickedRingAlias      // resource alias chosen by weighted pick before biome filter; "" otherwise
) {
    public boolean isNoVein() { return finalRecipe == null; }

    /** True when this no-vein outcome was caused by the synthetic EMPTY candidate winning the weighted pick. */
    public boolean isWeightedEmpty() {
        return finalRecipe == null && reason != null && reason.startsWith("weighted_empty");
    }

    /** True when this no-vein outcome was caused by the base no_vein_chance roll. */
    public boolean isBaseNoVein() {
        return finalRecipe == null && "no_vein_cell_roll".equals(reason);
    }
}
