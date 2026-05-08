package com.cciworld.policy;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record RegionPolicyRule(
    String name,
    boolean enabled,
    int weight,
    List<String> allowedBands,
    List<ResourceLocation> preferredRecipes,
    ResourceLocation replacementRecipe,
    List<WeightedReplacementEntry> replacementPool
) {}
