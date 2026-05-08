package com.cciworld.policy;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record BiomePolicyRule(
    String name,
    boolean enabled,
    ResourceLocation sourceRecipe,
    ResourceLocation replacementRecipe,
    Set<ResourceLocation> allowedBiomes
) {}
