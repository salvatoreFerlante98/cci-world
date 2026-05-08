package com.cciworld.policy;

import net.minecraft.resources.ResourceLocation;

public record RarityPolicyRule(
    String name,
    boolean enabled,
    ResourceLocation sourceRecipe,
    String band,
    double keepChance,
    ResourceLocation replacementRecipe
) {}
