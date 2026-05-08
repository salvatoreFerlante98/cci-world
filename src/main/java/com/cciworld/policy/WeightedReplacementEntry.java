package com.cciworld.policy;

import net.minecraft.resources.ResourceLocation;

public record WeightedReplacementEntry(ResourceLocation recipe, int weight) {}
