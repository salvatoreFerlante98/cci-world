package com.cciworld.policy;

import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
import com.tom.createores.OreData;
import com.tom.createores.OreDataAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public final class ResourcePolicyService {

    private ResourcePolicyService() {}

    /**
     * Inspects what the combined distance + biome + region + rarity policy would do without writing.
     * Caller must guarantee chunk is non-null and already loaded.
     */
    public static PolicyResult inspect(ServerLevel level, LevelChunk chunk) {
        return execute(level, chunk, false);
    }

    /**
     * Applies the combined distance + biome + region + rarity policy to an already-loaded chunk.
     * Each phase short-circuits if it applies a replacement.
     * This method never calls getChunk/getChunkAt.
     */
    public static PolicyResult apply(ServerLevel level, LevelChunk chunk) {
        return execute(level, chunk, true);
    }

    /** Exposes the rarity deterministic roll for selftest verification. */
    public static double testDeterministicRoll(String dimensionId, int chunkX, int chunkZ,
                                                String sourceRecipe, String bandName, String salt) {
        return RarityPolicyService.deterministicRoll(dimensionId, chunkX, chunkZ, sourceRecipe, bandName, salt);
    }

    /** Exposes the region deterministic roll for selftest verification. */
    public static double testDeterministicRegionRoll(String dimensionId, int regionX, int regionZ, String salt) {
        return RegionPolicyService.deterministicRegionRoll(dimensionId, regionX, regionZ, salt);
    }

    /** Exposes the pool deterministic roll for selftest verification. */
    public static double testDeterministicPoolRoll(String dimensionId, String regionName,
        int regionX, int regionZ, int chunkX, int chunkZ, String currentRecipe, String salt) {
        return RegionPolicyService.deterministicPoolRoll(dimensionId, regionName,
            regionX, regionZ, chunkX, chunkZ, currentRecipe, salt);
    }

    /** Returns the region name selected for the given region coordinates, or null if no valid rules exist. */
    public static String testRegionSelection(String dimensionId, int regionX, int regionZ) {
        String salt = CCIWorldConfig.REGION_SALT.get();
        double roll = RegionPolicyService.deterministicRegionRoll(dimensionId, regionX, regionZ, salt);
        List<RegionPolicyRule> valid = CCIWorldConfig.parseRegionRules().stream()
            .filter(r -> r.enabled() && r.weight() > 0)
            .toList();
        RegionPolicyRule selected = RegionPolicyService.selectByWeight(valid, roll);
        return selected != null ? selected.name() : null;
    }

    /** Exposes pool weighted selection for selftest: returns chosen recipe or null. */
    public static ResourceLocation testPoolSelection(List<WeightedReplacementEntry> pool, double roll) {
        return RegionPolicyService.selectFromPool(pool, roll);
    }

    private static PolicyResult execute(ServerLevel level, LevelChunk chunk, boolean doApply) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        if (!CCIWorldConfig.ENABLED.get()) {
            return new PolicyResult(PolicyResult.Reason.DISABLED, PolicyResult.PolicyType.NONE,
                null, -1, -1, null, null, null, null, 0, cx, cz,
                null, 0.0, 0.0, false,
                0, 0, null, 0.0, false, null, null,
                false, 0.0, null, 0);
        }

        BlockPos spawnPos = level.getSharedSpawnPos();
        int spawnCX = SectionPos.blockToSectionCoord(spawnPos.getX());
        int spawnCZ = SectionPos.blockToSectionCoord(spawnPos.getZ());
        int distChunks = Math.max(Math.abs(cx - spawnCX), Math.abs(cz - spawnCZ));

        List<DistanceBand> bands = CCIWorldConfig.parseBands();
        DistanceBand band = null;
        for (DistanceBand b : bands) {
            if (b.contains(distChunks)) { band = b; break; }
        }
        String bandName = band != null ? band.name() : null;
        int bandMin    = band != null ? band.minDistance() : -1;
        int bandMax    = band != null ? band.maxDistance() : -1;

        OreData data = OreDataAttachment.getData(chunk);
        ResourceLocation currentId = data.getRecipeId();

        if (currentId == null) {
            return new PolicyResult(PolicyResult.Reason.NO_CURRENT_RECIPE, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, null, null, null, null, distChunks, cx, cz,
                null, 0.0, 0.0, false,
                0, 0, null, 0.0, false, null, null,
                false, 0.0, null, 0);
        }

        // --- Phase 1: distance band policy ---
        if (band != null) {
            ResourceLocation bandTarget = band.replacements().get(currentId);
            if (bandTarget != null) {
                if (!COEVeinWriter.veinExists(level.getServer().getRecipeManager(), bandTarget)) {
                    return new PolicyResult(PolicyResult.Reason.TARGET_RECIPE_MISSING, PolicyResult.PolicyType.DISTANCE,
                        bandName, bandMin, bandMax, null, null, currentId, bandTarget, distChunks, cx, cz,
                        null, 0.0, 0.0, false,
                        0, 0, null, 0.0, false, null, null,
                        false, 0.0, null, 0);
                }
                if (doApply) {
                    COEVeinWriter.writeVein(chunk, bandTarget, CCIWorldConfig.RANDOM_MULTIPLIER.get().floatValue());
                }
                return new PolicyResult(PolicyResult.Reason.APPLIED, PolicyResult.PolicyType.DISTANCE,
                    bandName, bandMin, bandMax, null, null, currentId, bandTarget, distChunks, cx, cz,
                    null, 0.0, 0.0, false,
                    0, 0, null, 0.0, false, null, null,
                    false, 0.0, null, 0);
            }
        }

        // --- Phase 2: biome policy ---
        ResourceLocation biomeId = null;
        String matchedBiomeRule = null;
        if (CCIWorldConfig.BIOME_POLICY_ENABLED.get()) {
            PolicyResult biomeResult = BiomePolicyService.evaluate(
                level, chunk, bandName, bandMin, bandMax, currentId, distChunks, doApply);
            if (biomeResult.applied()) return biomeResult;
            biomeId = biomeResult.biomeId();
            matchedBiomeRule = biomeResult.matchedBiomeRule();
        }

        // --- Phase 3: region policy ---
        PolicyResult regionContext = null;
        if (CCIWorldConfig.REGION_POLICY_ENABLED.get()) {
            regionContext = RegionPolicyService.evaluate(
                level, chunk, bandName, bandMin, bandMax,
                currentId, biomeId, matchedBiomeRule, distChunks, doApply);
            if (regionContext.applied()) return regionContext;
        }

        // Extract region fields to carry into rarity / final result
        int regX    = regionContext != null ? regionContext.regionX()                         : 0;
        int regZ    = regionContext != null ? regionContext.regionZ()                         : 0;
        String regN = regionContext != null ? regionContext.regionName()                      : null;
        double regR = regionContext != null ? regionContext.regionRoll()                      : 0.0;
        boolean regA = regionContext != null && regionContext.regionAllowed();
        List<ResourceLocation> regP = regionContext != null ? regionContext.regionPreferredRecipes()  : null;
        ResourceLocation regRep     = regionContext != null ? regionContext.regionReplacementRecipe() : null;

        // --- Phase 4: rarity gate ---
        if (!CCIWorldConfig.RARITY_POLICY_ENABLED.get()) {
            PolicyResult.Reason reason = (band == null)
                ? PolicyResult.Reason.NO_MATCHING_BAND
                : PolicyResult.Reason.RARITY_POLICY_DISABLED;
            return new PolicyResult(reason, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                currentId, null, distChunks, cx, cz,
                null, 0.0, 0.0, false,
                regX, regZ, regN, regR, regA, regP, regRep,
                false, 0.0, null, 0);
        }

        return RarityPolicyService.evaluate(
            level, chunk, bandName, bandMin, bandMax,
            currentId, biomeId, matchedBiomeRule, regionContext, distChunks, doApply);
    }
}
