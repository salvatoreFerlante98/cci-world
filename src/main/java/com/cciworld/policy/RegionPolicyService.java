package com.cciworld.policy;

import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RegionPolicyService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> WARNED_POOL_MISSING = ConcurrentHashMap.newKeySet();

    private RegionPolicyService() {}

    static PolicyResult evaluate(
        ServerLevel level,
        LevelChunk chunk,
        String bandName,
        int bandMin,
        int bandMax,
        ResourceLocation currentId,
        ResourceLocation biomeId,
        String matchedBiomeRule,
        int distChunks,
        boolean doApply
    ) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        int regionSizeChunks = CCIWorldConfig.REGION_SIZE_CHUNKS.get();
        int regionX = Math.floorDiv(cx, regionSizeChunks);
        int regionZ = Math.floorDiv(cz, regionSizeChunks);

        List<RegionPolicyRule> rules = CCIWorldConfig.parseRegionRules();
        List<RegionPolicyRule> valid = rules.stream()
            .filter(r -> r.enabled() && r.weight() > 0)
            .toList();

        if (valid.isEmpty()) {
            return new PolicyResult(
                PolicyResult.Reason.REGION_NO_VALID_RULE, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                currentId, null, distChunks, cx, cz,
                null, 0.0, 0.0, false,
                regionX, regionZ, null, 0.0, false, null, null,
                false, 0.0, null, 0);
        }

        String dimensionId = level.dimension().location().toString();
        String salt = CCIWorldConfig.REGION_SALT.get();
        double roll = deterministicRegionRoll(dimensionId, regionX, regionZ, salt);
        RegionPolicyRule selected = selectByWeight(valid, roll);

        if (selected == null) {
            return new PolicyResult(
                PolicyResult.Reason.REGION_NO_VALID_RULE, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                currentId, null, distChunks, cx, cz,
                null, 0.0, 0.0, false,
                regionX, regionZ, null, roll, false, null, null,
                false, 0.0, null, 0);
        }

        // Check if current band is permitted by this region
        if (bandName == null || !selected.allowedBands().contains(bandName)) {
            return new PolicyResult(
                PolicyResult.Reason.REGION_NO_MATCHING_BAND, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                currentId, null, distChunks, cx, cz,
                null, 0.0, 0.0, false,
                regionX, regionZ, selected.name(), roll, false,
                selected.preferredRecipes(), selected.replacementRecipe(),
                false, 0.0, null, 0);
        }

        // Check if current recipe is preferred by this region
        if (selected.preferredRecipes().contains(currentId)) {
            return new PolicyResult(
                PolicyResult.Reason.REGION_ALLOWED, PolicyResult.PolicyType.REGION,
                bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                currentId, null, distChunks, cx, cz,
                null, 0.0, 0.0, false,
                regionX, regionZ, selected.name(), roll, true,
                selected.preferredRecipes(), selected.replacementRecipe(),
                false, 0.0, null, 0);
        }

        // Recipe not preferred — determine replacement via pool or fallback
        boolean poolEnabled = CCIWorldConfig.WEIGHTED_REPLACEMENT_POOLS_ENABLED.get();
        List<WeightedReplacementEntry> pool = selected.replacementPool();
        String poolSalt = CCIWorldConfig.REPLACEMENT_POOL_SALT.get();

        boolean poolUsed = false;
        double poolRoll = 0.0;
        ResourceLocation poolChoice = null;
        int poolTotalWeight = 0;
        ResourceLocation target;

        if (poolEnabled && !pool.isEmpty()) {
            var rm = level.getServer().getRecipeManager();
            List<WeightedReplacementEntry> validPool = new ArrayList<>();
            for (WeightedReplacementEntry e : pool) {
                if (COEVeinWriter.veinExists(rm, e.recipe())) {
                    validPool.add(e);
                } else if (WARNED_POOL_MISSING.add(e.recipe())) {
                    LOGGER.warn("[CCI World] region rule '{}': pool entry '{}' not found in RecipeManager — skipped",
                        selected.name(), e.recipe());
                }
            }
            poolTotalWeight = validPool.stream().mapToInt(WeightedReplacementEntry::weight).sum();
            if (poolTotalWeight > 0) {
                poolRoll = deterministicPoolRoll(dimensionId, selected.name(), regionX, regionZ,
                    cx, cz, currentId.toString(), poolSalt);
                poolChoice = selectFromPool(validPool, poolRoll);
            }
        }

        if (poolChoice != null) {
            poolUsed = true;
            target = poolChoice;
        } else {
            target = selected.replacementRecipe();
        }

        // If fallback target doesn't exist
        if (!poolUsed) {
            if (!COEVeinWriter.veinExists(level.getServer().getRecipeManager(), target)) {
                PolicyResult.Reason reason = (poolEnabled && !pool.isEmpty() && poolTotalWeight <= 0)
                    ? PolicyResult.Reason.REGION_REPLACEMENT_POOL_INVALID
                    : PolicyResult.Reason.REGION_TARGET_MISSING;
                return new PolicyResult(
                    reason, PolicyResult.PolicyType.REGION,
                    bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                    currentId, target, distChunks, cx, cz,
                    null, 0.0, 0.0, false,
                    regionX, regionZ, selected.name(), roll, false,
                    selected.preferredRecipes(), selected.replacementRecipe(),
                    false, poolRoll, null, poolTotalWeight);
            }
        }

        if (doApply) {
            COEVeinWriter.writeVein(chunk, target, CCIWorldConfig.RANDOM_MULTIPLIER.get().floatValue());
        }

        PolicyResult.Reason reason = poolUsed
            ? PolicyResult.Reason.REGION_NOT_PREFERRED_WEIGHTED_APPLIED
            : PolicyResult.Reason.REGION_NOT_PREFERRED_APPLIED;

        return new PolicyResult(
            reason, PolicyResult.PolicyType.REGION,
            bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
            currentId, target, distChunks, cx, cz,
            null, 0.0, 0.0, false,
            regionX, regionZ, selected.name(), roll, false,
            selected.preferredRecipes(), selected.replacementRecipe(),
            poolUsed, poolRoll, poolChoice, poolTotalWeight);
    }

    /**
     * SHA-256 deterministic roll at region granularity.
     * All chunks within the same (regionX, regionZ) cell share the same roll and therefore the same region type.
     */
    static double deterministicRegionRoll(String dimensionId, int regionX, int regionZ, String salt) {
        String input = dimensionId + "|" + regionX + "|" + regionZ + "|" + salt;
        return sha256ToDouble(input);
    }

    /**
     * SHA-256 deterministic pool roll at chunk granularity.
     * Same chunk + recipe + region + salt always yields the same pool choice.
     */
    static double deterministicPoolRoll(String dimensionId, String regionName,
                                         int regionX, int regionZ, int chunkX, int chunkZ,
                                         String currentRecipe, String salt) {
        String input = dimensionId + "|" + regionName + "|" + regionX + "|" + regionZ
            + "|" + chunkX + "|" + chunkZ + "|" + currentRecipe + "|" + salt;
        return sha256ToDouble(input);
    }

    private static double sha256ToDouble(String input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(input.getBytes(StandardCharsets.UTF_8));
            long raw = 0;
            for (int i = 0; i < 8; i++) {
                raw = (raw << 8) | (hash[i] & 0xFF);
            }
            return (raw >>> 11) / (double)(1L << 53);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static RegionPolicyRule selectByWeight(List<RegionPolicyRule> rules, double roll) {
        int totalWeight = rules.stream().mapToInt(RegionPolicyRule::weight).sum();
        if (totalWeight <= 0) return null;
        double target = roll * totalWeight;
        double cumulative = 0.0;
        for (RegionPolicyRule rule : rules) {
            cumulative += rule.weight();
            if (target < cumulative) return rule;
        }
        return rules.get(rules.size() - 1);
    }

    static ResourceLocation selectFromPool(List<WeightedReplacementEntry> pool, double roll) {
        int totalWeight = pool.stream().mapToInt(WeightedReplacementEntry::weight).sum();
        if (totalWeight <= 0) return null;
        double target = roll * totalWeight;
        double cumulative = 0.0;
        for (WeightedReplacementEntry e : pool) {
            cumulative += e.weight();
            if (target < cumulative) return e.recipe();
        }
        return pool.get(pool.size() - 1).recipe();
    }
}
