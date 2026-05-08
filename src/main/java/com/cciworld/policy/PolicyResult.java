package com.cciworld.policy;

import net.minecraft.resources.ResourceLocation;

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
    int chunkZ
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
        BIOME_ID_INVALID_CONFIG
    }

    public enum PolicyType { DISTANCE, BIOME, NONE }

    public boolean applied() {
        return reason == Reason.APPLIED || reason == Reason.BIOME_NOT_ALLOWED_APPLIED;
    }
}
