package com.cciworld.policy;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record PolicyResult(
    Reason reason,
    PolicyType policyType,
    String bandName,
    int bandMin,
    int bandMax,
    ResourceLocation biomeId,
    String matchedBiomeRule,
    ResourceLocation previousRecipe,
    ResourceLocation newRecipe,
    int distChunks,
    int chunkX,
    int chunkZ,
    // rarity
    String rarityRuleName,
    double rarityKeepChance,
    double rarityRoll,
    boolean rarityPassed,
    // region
    int regionX,
    int regionZ,
    String regionName,
    double regionRoll,
    boolean regionAllowed,
    List<ResourceLocation> regionPreferredRecipes,
    ResourceLocation regionReplacementRecipe,
    // region weighted pool
    boolean regionReplacementPoolUsed,
    double regionReplacementPoolRoll,
    ResourceLocation regionReplacementPoolChoice,
    int regionReplacementPoolTotalWeight
) {
    public enum Reason {
        DISABLED,
        NO_CURRENT_RECIPE,
        NO_MATCHING_BAND,
        NO_MATCHING_RULE,
        TARGET_RECIPE_MISSING,
        APPLIED,
        CHUNK_NOT_LOADED,
        BIOME_POLICY_DISABLED,
        BIOME_UNKNOWN,
        BIOME_ALLOWED,
        BIOME_NOT_ALLOWED_APPLIED,
        BIOME_RULE_TARGET_MISSING,
        BIOME_ID_INVALID_CONFIG,
        REGION_POLICY_DISABLED,
        REGION_NO_VALID_RULE,
        REGION_NO_MATCHING_BAND,
        REGION_ALLOWED,
        REGION_NOT_PREFERRED_APPLIED,
        REGION_NOT_PREFERRED_WEIGHTED_APPLIED,
        REGION_TARGET_MISSING,
        REGION_REPLACEMENT_POOL_INVALID,
        REGION_RULE_INVALID_CONFIG,
        RARITY_POLICY_DISABLED,
        RARITY_NO_MATCHING_RULE,
        RARITY_GATE_PASSED,
        RARITY_GATE_FAILED_APPLIED,
        RARITY_RULE_TARGET_MISSING,
        RARITY_RULE_INVALID_CONFIG
    }

    public enum PolicyType { DISTANCE, BIOME, REGION, RARITY, NONE }

    public boolean applied() {
        return reason == Reason.APPLIED
            || reason == Reason.BIOME_NOT_ALLOWED_APPLIED
            || reason == Reason.REGION_NOT_PREFERRED_APPLIED
            || reason == Reason.REGION_NOT_PREFERRED_WEIGHTED_APPLIED
            || reason == Reason.RARITY_GATE_FAILED_APPLIED;
    }
}
