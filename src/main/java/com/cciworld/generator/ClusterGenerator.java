package com.cciworld.generator;

import com.cciworld.coe.COEVeinIds;
import com.cciworld.config.CCIWorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative cluster generator (CCI World v0.5.1, cell-based).
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>The world is partitioned into square cells of {@code cell_size_chunks}
 *       chunks. Each chunk belongs to exactly one cell.</li>
 *   <li>For each cell a deterministic hash of (worldSeed, cellX, cellZ) decides
 *       (a) whether the cell has any cluster at all (no_vein_chance), (b) the
 *       cluster center as a (blockX,blockZ) inside the cell, (c) the cluster
 *       radius in blocks, (d) the resource based on the spawn-distance ring of
 *       the center plus weighted pick.</li>
 *   <li>A chunk is "inside the vein" if the euclidean distance between its
 *       center and the cluster center is ≤ radius. Otherwise it is no-vein.</li>
 * </ol>
 *
 * <p>Pure / deterministic: no I/O, no chunk generation forced. Uses only data
 * read from the {@link ServerLevel} (seed, spawn, biome lookup).</p>
 */
public final class ClusterGenerator {

    private static final long SALT_CELL    = 0x6363695F77726C64L; // "cci_wrld"
    private static final long SALT_CENTER  = 0x636C75737463746CL; // "clustctl"
    private static final long SALT_RADIUS  = 0x7261646975735F5FL; // "radius__"
    private static final long SALT_PICK    = 0x636C75737465725FL; // "cluster_"

    private ClusterGenerator() {}

    /** Resource ring: distance-from-spawn band, weight, radius range (blocks), units per chunk. */
    public record Ring(String alias, int minBlocks, int maxBlocks, int weight,
                       int radiusMinBlocks, int radiusMaxBlocks,
                       long unitsPerChunk,
                       ResourceLocation recipeId) {}

    /** Build the ring set from current config values. */
    public static List<Ring> rings() {
        List<Ring> r = new ArrayList<>(6);
        r.add(new Ring("coal",
            CCIWorldConfig.GEN_COAL_MIN.get(), CCIWorldConfig.GEN_COAL_MAX.get(),
            CCIWorldConfig.GEN_COAL_WEIGHT.get(),
            CCIWorldConfig.GEN_COAL_RMIN.get(), CCIWorldConfig.GEN_COAL_RMAX.get(),
            CCIWorldConfig.GEN_COAL_UNITS.get(),
            COEVeinIds.fromAlias("coal").orElseThrow()));
        r.add(new Ring("iron",
            CCIWorldConfig.GEN_IRON_MIN.get(), CCIWorldConfig.GEN_IRON_MAX.get(),
            CCIWorldConfig.GEN_IRON_WEIGHT.get(),
            CCIWorldConfig.GEN_IRON_RMIN.get(), CCIWorldConfig.GEN_IRON_RMAX.get(),
            CCIWorldConfig.GEN_IRON_UNITS.get(),
            COEVeinIds.fromAlias("iron").orElseThrow()));
        r.add(new Ring("copper",
            CCIWorldConfig.GEN_COPPER_MIN.get(), CCIWorldConfig.GEN_COPPER_MAX.get(),
            CCIWorldConfig.GEN_COPPER_WEIGHT.get(),
            CCIWorldConfig.GEN_COPPER_RMIN.get(), CCIWorldConfig.GEN_COPPER_RMAX.get(),
            CCIWorldConfig.GEN_COPPER_UNITS.get(),
            COEVeinIds.fromAlias("copper").orElseThrow()));
        r.add(new Ring("zinc",
            CCIWorldConfig.GEN_ZINC_MIN.get(), CCIWorldConfig.GEN_ZINC_MAX.get(),
            CCIWorldConfig.GEN_ZINC_WEIGHT.get(),
            CCIWorldConfig.GEN_ZINC_RMIN.get(), CCIWorldConfig.GEN_ZINC_RMAX.get(),
            CCIWorldConfig.GEN_ZINC_UNITS.get(),
            COEVeinIds.fromAlias("zinc").orElseThrow()));
        r.add(new Ring("redstone",
            CCIWorldConfig.GEN_REDSTONE_MIN.get(), CCIWorldConfig.GEN_REDSTONE_MAX.get(),
            CCIWorldConfig.GEN_REDSTONE_WEIGHT.get(),
            CCIWorldConfig.GEN_REDSTONE_RMIN.get(), CCIWorldConfig.GEN_REDSTONE_RMAX.get(),
            CCIWorldConfig.GEN_REDSTONE_UNITS.get(),
            COEVeinIds.fromAlias("redstone").orElseThrow()));
        r.add(new Ring("gold",
            CCIWorldConfig.GEN_GOLD_MIN.get(), CCIWorldConfig.GEN_GOLD_MAX.get(),
            CCIWorldConfig.GEN_GOLD_WEIGHT.get(),
            CCIWorldConfig.GEN_GOLD_RMIN.get(), CCIWorldConfig.GEN_GOLD_RMAX.get(),
            CCIWorldConfig.GEN_GOLD_UNITS.get(),
            COEVeinIds.fromAlias("gold").orElseThrow()));
        return r;
    }

    public static int cellSizeChunks() {
        return CCIWorldConfig.GEN_CELL_SIZE_CHUNKS.get();
    }

    /** Configured target units-per-chunk for the given recipe id, or -1 if unknown. */
    public static long unitsForRecipeId(ResourceLocation recipeId) {
        if (recipeId == null) return -1L;
        for (Ring r : rings()) {
            if (r.recipeId().equals(recipeId)) return r.unitsPerChunk();
        }
        return -1L;
    }

    public static double noVeinChance() {
        return CCIWorldConfig.GEN_NO_VEIN_CHANCE.get();
    }

    /** Floor-divide for cell-grid indexing (works correctly on negative coords). */
    public static int cellOf(int chunkCoord, int cellSize) {
        return Math.floorDiv(chunkCoord, cellSize);
    }

    /** Pure decision routine. Reads spawn/seed/biome but does NOT write OreData. */
    public static ClusterDecision decide(ServerLevel level, LevelChunk chunk) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        BlockPos spawn = level.getSharedSpawnPos();
        int chunkCenterX = (cx << 4) + 8;
        int chunkCenterZ = (cz << 4) + 8;
        long ddx = chunkCenterX - spawn.getX();
        long ddz = chunkCenterZ - spawn.getZ();
        int distance = (int) Math.round(Math.sqrt((double) (ddx * ddx + ddz * ddz)));

        // Biome at chunk center (informational only, no biome logic in v0.5.1)
        int sampleY = CCIWorldConfig.BIOME_SAMPLE_Y.get();
        Holder<Biome> biomeHolder = level.getBiome(new BlockPos(chunkCenterX, sampleY, chunkCenterZ));
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);

        long worldSeed = level.getSeed();

        int cellSize = cellSizeChunks();
        int cellX = cellOf(cx, cellSize);
        int cellZ = cellOf(cz, cellSize);

        long cellHash = splitmix(worldSeed ^ SALT_CELL, cellX, cellZ);
        double rollNoVein = toUnit(cellHash);

        // Cell-level no-vein roll: if hit, the whole cell is barren.
        if (rollNoVein < noVeinChance()) {
            return new ClusterDecision(cx, cz, distance, biomeId,
                cellX, cellZ, 0, 0, 0, 0,
                null, "no_vein_cell_roll", cellHash, rollNoVein);
        }

        // Cluster center: pick a (blockX,blockZ) deterministically inside the cell.
        int cellSizeBlocks = cellSize * 16;
        int cellOriginX = cellX * cellSizeBlocks;
        int cellOriginZ = cellZ * cellSizeBlocks;
        long centerHash = splitmix(worldSeed ^ SALT_CENTER, cellX, cellZ);
        // split centerHash into two 32-bit halves to pick X and Z offsets
        int offX = (int) Math.floorMod(centerHash >>> 32, (long) cellSizeBlocks);
        int offZ = (int) Math.floorMod(centerHash & 0xFFFFFFFFL, (long) cellSizeBlocks);
        int centerX = cellOriginX + offX;
        int centerZ = cellOriginZ + offZ;

        // Cluster ring: pick using the SPAWN-distance of the cluster center (so the
        // whole cluster shares the same ring choice).
        long centerDx = centerX - spawn.getX();
        long centerDz = centerZ - spawn.getZ();
        int centerDistance = (int) Math.round(Math.sqrt((double)(centerDx * centerDx + centerDz * centerDz)));

        List<Ring> rings = rings();
        List<Ring> candidates = new ArrayList<>(rings.size());
        int totalWeight = 0;
        for (Ring r : rings) {
            if (centerDistance >= r.minBlocks() && centerDistance <= r.maxBlocks()) {
                candidates.add(r);
                totalWeight += r.weight();
            }
        }

        // Distance from the chunk's center to the cluster center
        long fdx = chunkCenterX - centerX;
        long fdz = chunkCenterZ - centerZ;
        int distFromCenter = (int) Math.round(Math.sqrt((double)(fdx * fdx + fdz * fdz)));

        if (candidates.isEmpty() || totalWeight <= 0) {
            return new ClusterDecision(cx, cz, distance, biomeId,
                cellX, cellZ, centerX, centerZ, 0, distFromCenter,
                null, "out_of_all_rings", cellHash, rollNoVein);
        }

        long pickHash = splitmix(worldSeed ^ SALT_PICK, cellX, cellZ);
        double rollPick = toUnit(pickHash);
        int target = (int) Math.floor(rollPick * totalWeight);
        if (target >= totalWeight) target = totalWeight - 1;

        int acc = 0;
        Ring chosen = candidates.get(candidates.size() - 1);
        for (Ring r : candidates) {
            acc += r.weight();
            if (target < acc) { chosen = r; break; }
        }

        // Cluster radius — uniform within the ring's [radius_min, radius_max].
        long radiusHash = splitmix(worldSeed ^ SALT_RADIUS, cellX, cellZ);
        double rollRadius = toUnit(radiusHash);
        int rMin = chosen.radiusMinBlocks();
        int rMax = chosen.radiusMaxBlocks();
        if (rMax < rMin) rMax = rMin;
        int radius = rMin + (int) Math.floor(rollRadius * (rMax - rMin + 1));
        if (radius > rMax) radius = rMax;

        if (distFromCenter > radius) {
            return new ClusterDecision(cx, cz, distance, biomeId,
                cellX, cellZ, centerX, centerZ, radius, distFromCenter,
                null, "outside_cluster_radius:" + chosen.alias(), cellHash, rollPick);
        }

        return new ClusterDecision(cx, cz, distance, biomeId,
            cellX, cellZ, centerX, centerZ, radius, distFromCenter,
            chosen.recipeId(), "in_cluster:" + chosen.alias(), cellHash, rollPick);
    }

    // -- deterministic hashing -----------------------------------------------

    public static long splitmix(long seed, int x, int z) {
        long h = seed;
        h ^= ((long) x) * 0x9E3779B97F4A7C15L;
        h = mix(h);
        h ^= ((long) z) * 0xC2B2AE3D27D4EB4FL;
        h = mix(h);
        return h;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static double toUnit(long hash) {
        return (hash >>> 11) / (double) (1L << 53);
    }
}
